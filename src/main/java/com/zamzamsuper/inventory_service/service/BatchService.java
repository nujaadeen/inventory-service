package com.zamzamsuper.inventory_service.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import com.zamzamsuper.inventory_service.exception.FinancialMismatchException;
import com.zamzamsuper.inventory_service.exception.InvalidStatusException;
import jakarta.annotation.Nonnull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zamzamsuper.inventory_service.dto.batch.BatchCreateRequest;
import com.zamzamsuper.inventory_service.dto.batch.BatchResponse;
import com.zamzamsuper.inventory_service.dto.batch.BatchUpdateRequest;
import com.zamzamsuper.inventory_service.enums.BatchStatus;
import com.zamzamsuper.inventory_service.enums.GRNStatus;
import com.zamzamsuper.inventory_service.exception.StockConflictException;
import com.zamzamsuper.inventory_service.mapper.BatchMapper;
import com.zamzamsuper.inventory_service.mapper.ProductPriceMapper;
import com.zamzamsuper.inventory_service.model.Batch;
import com.zamzamsuper.inventory_service.model.GRN;
import com.zamzamsuper.inventory_service.model.Location;
import com.zamzamsuper.inventory_service.model.ProductPrice;
import com.zamzamsuper.inventory_service.model.Stock;
import com.zamzamsuper.inventory_service.repository.BatchRepository;
import com.zamzamsuper.inventory_service.repository.GRNRepository;
import com.zamzamsuper.inventory_service.repository.LocationRepository;
import com.zamzamsuper.inventory_service.repository.StockRepository;
import com.zamzamsuper.inventory_service.repository.specification.BatchSpecification;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchMapper batchMapper;
    private final ProductPriceMapper priceMapper;
    private final BatchRepository batchRepository;
    private final GRNRepository grnRepository;
    private final StockRepository stockRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public BatchResponse createBatch(@Nonnull BatchCreateRequest request) {
        log.info("Starting standalone Batch creation linked to GRN ID: {}", request.grnId());

        // 1. Fetch Parent GRN
        GRN grn = grnRepository.findById(request.grnId())
                .orElseThrow(() -> new EntityNotFoundException("Parent GRN not found with ID: " + request.grnId()));

        if (grn.getStatus() == GRNStatus.CANCELLED) {
            throw new InvalidStatusException("Cannot add a new batch to a CANCELLED GRN.");
        }

        // 2. Resolve Stock & Increment atomically
        Stock stock = getOrCreateStock(request.productSku(), request.locationId());
        stockRepository.incrementQuantity(stock.getId(), request.quantity());

        // 3. Financial Adjustments to parent GRN
        BigDecimal batchTotalValue = request.costPrice().multiply(request.quantity());
        adjustGRNFinancials(grn, batchTotalValue);

        // 4. Map Header and link relationships
        Batch batch = batchMapper.toEntity(request);
        batch.setGrn(grn);
        batch.setStock(stock);

        // 5. Process nested prices
        request.prices().forEach(pReq -> {
            ProductPrice price = priceMapper.toEntity(pReq);
            batch.addPrice(price);
        });

        // 6. Save and Return
        Batch savedBatch = batchRepository.save(batch);
        return batchMapper.toResponse(savedBatch);
    }

    @Transactional(readOnly = true)
    public Page<BatchResponse> getAllBatches(
            String productSku,
            String batchNumber,
            BatchStatus status,
            LocalDate expiryStartDate,
            LocalDate expiryEndDate,
            Pageable pageable) {

        log.debug("Fetching paginated batches with filters");

        // 1. Get Paged IDs with Filters
        Specification<Batch> spec = BatchSpecification.withFilters(
                productSku, batchNumber, status, expiryStartDate, expiryEndDate);

        Page<Batch> batchPage = batchRepository.findAll(spec, pageable);

        if (batchPage.isEmpty()) return Page.empty(pageable);

        List<Long> batchIds = batchPage.getContent().stream().map(Batch::getId).toList();

        // 2. Fetch Batches + Prices (Mitigates N+1 problem)
        List<Batch> batchesWithPrices = batchRepository.findAllByIdWithPrices(batchIds);

        // 3. Map to DTOs
        List<BatchResponse> listOfDto = batchesWithPrices.stream()
                .map(batchMapper::toResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(listOfDto, pageable, batchPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public BatchResponse getBatchById(Long id) {
        log.info("Fetching Batch ID: {} with optimized joins", id);

        Batch batch = batchRepository.findByIdWithPrices(id)
                .orElseThrow(() -> new EntityNotFoundException("Batch not found with id " + id));

        return batchMapper.toResponse(batch);
    }

    @Transactional
    public BatchResponse updateBatch(Long id, @Nonnull BatchUpdateRequest request) {
        log.info("Updating Batch | ID: {}", id);

        Batch existingBatch = batchRepository.findByIdWithPrices(id)
                .orElseThrow(() -> new EntityNotFoundException("Batch not found with id " + id));

        if (existingBatch.getStatus() == BatchStatus.CANCELLED) {
            throw new InvalidStatusException("Cannot update a cancelled Batch");
        }

        // 1. Non-critical field updates
        if (request.expiryDate() != null) {
            existingBatch.setExpiryDate(request.expiryDate());
        }

        // 2. Financial & Stock Differential Calculations
        BigDecimal oldQuantity = existingBatch.getQuantity();
        BigDecimal oldCostPrice = existingBatch.getCostPrice();
        BigDecimal oldTotalValue = oldCostPrice.multiply(oldQuantity);

        BigDecimal newQuantity = request.quantity() != null ? request.quantity() : oldQuantity;
        BigDecimal newCostPrice = request.costPrice() != null ? request.costPrice() : oldCostPrice;
        BigDecimal newTotalValue = newCostPrice.multiply(newQuantity);

        // 3. Apply Stock Updates if quantity changed
        if (newQuantity.compareTo(oldQuantity) != 0) {
            BigDecimal qtyDifference = newQuantity.subtract(oldQuantity);

            if (qtyDifference.compareTo(BigDecimal.ZERO) > 0) {
                stockRepository.incrementQuantity(existingBatch.getStock().getId(), qtyDifference);
            } else {
                // We use abs() to pass a positive decrement value to the repository
                int updated = stockRepository.decrementQuantity(existingBatch.getStock().getId(), qtyDifference.abs());
                if (updated == 0) {
                    throw new StockConflictException(existingBatch.getStock().getProductSku());
                }
            }
            existingBatch.setQuantity(newQuantity);
        }

        // 4. Apply Cost updates if changed
        if (newCostPrice.compareTo(oldCostPrice) != 0) {
            existingBatch.setCostPrice(newCostPrice);
        }

        // 5. Apply Financial Differential to Parent GRN
        BigDecimal valueDifference = newTotalValue.subtract(oldTotalValue);
        if (valueDifference.compareTo(BigDecimal.ZERO) != 0) {
            adjustGRNFinancials(existingBatch.getGrn(), valueDifference);
        }

        log.debug("Batch updated successfully | ID: {}", id);
        return batchMapper.toResponse(existingBatch);
    }

    @Transactional
    public BatchResponse cancelBatch(Long id) {
        log.info("Starting Batch cancellation process | Batch_ID={}", id);

        Batch batch = batchRepository.findByIdWithPrices(id)
                .orElseThrow(() -> {
                    log.error("Batch not found | Batch_ID={}", id);
                    return new EntityNotFoundException("Batch not found");
                });

        // 1. Status check (Idempotency check)
        if (batch.getStatus() == BatchStatus.CANCELLED) {
            log.warn("Batch already cancelled | Batch_ID={}", id);
            throw new InvalidStatusException("Batch already cancelled");
        }

        log.debug("Reverting stock | Stock_ID={}, SKU={}, Qty={}",
                batch.getStock().getId(), batch.getStock().getProductSku(), batch.getQuantity());

        // 2. Revert Stock Atomically
        int updated = stockRepository.decrementQuantity(batch.getStock().getId(), batch.getQuantity());

        if (updated == 0) {
            log.error("Stock conflict detected | Stock_ID={}, SKU={}, RequiredQty={}",
                    batch.getStock().getId(), batch.getStock().getProductSku(), batch.getQuantity());
            throw new StockConflictException(batch.getStock().getProductSku());
        }

        // 3. Revert Financials on Parent GRN
        BigDecimal batchTotalValue = batch.getCostPrice().multiply(batch.getQuantity());
        adjustGRNFinancials(batch.getGrn(), batchTotalValue.negate()); // Note the .negate()!

        // 4. Soft Delete Batch
        batch.setStatus(BatchStatus.CANCELLED);

        // 5. Soft Delete Associated Prices
        batch.getPrices().forEach(price -> {
            price.setActive(false);
            log.trace("Price disabled | Price_ID={}", price.getId());
        });

        log.info("Batch cancellation completed successfully | Batch_ID={}", id);
        return batchMapper.toResponse(batch);
    }

    @Transactional
    public void purgeCancelledBatch(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Batch not found"));

        // SAFETY CHECK: Only CANCELLED batches can be purged
        if (batch.getStatus() != BatchStatus.CANCELLED) {
            log.error("Security violation: Attempted to purge a non-cancelled Batch | ID: {} | Status: {}",
                    id, batch.getStatus());
            throw new InvalidStatusException("Only CANCELLED Batches can be permanently deleted. Cancel the batch first.");
        }

        // Because we have CascadeType.ALL on prices inside the Batch entity,
        // deleting the batch cleans up the child prices automatically.
        batchRepository.delete(batch);

        log.info("Batch ID: {} and all associated prices have been permanently purged from the database.", id);
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private Stock getOrCreateStock(String productSku, Long locationId) {
        return stockRepository.findByProductSkuAndLocationId(productSku, locationId)
                .orElseGet(() -> {
                    Location location = locationRepository.findById(locationId)
                            .orElseThrow(() -> new EntityNotFoundException("Location not found"));

                    return stockRepository.save(
                            Stock.builder()
                                    .productSku(productSku)
                                    .location(location)
                                    .quantityOnHand(BigDecimal.ZERO)
                                    .build());
                });
    }

    private void adjustGRNFinancials(GRN grn, BigDecimal amountDifference) {
        BigDecimal currentSubTotal = grn.getSubTotalAmount() != null ? grn.getSubTotalAmount() : BigDecimal.ZERO;
        BigDecimal currentDiscount = grn.getTotalDiscount() != null ? grn.getTotalDiscount() : BigDecimal.ZERO;

        BigDecimal newSubTotal = currentSubTotal.add(amountDifference);
        BigDecimal newGrandTotal = newSubTotal.subtract(currentDiscount);

        if (newGrandTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new FinancialMismatchException(
                    "This action causes the GRN Grand Total to drop below zero (likely due to existing flat discounts on the GRN). " +
                            "Adjust the GRN discount before altering this batch.");
        }

        grn.setSubTotalAmount(newSubTotal);
        grn.setGrandTotalAmount(newGrandTotal);
        log.debug("Adjusted GRN Financials | GRN_ID={} | New SubTotal={} | New GrandTotal={}",
                grn.getId(), newSubTotal, newGrandTotal);
    }
}