package com.zamzamsuper.inventory_service.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.Nonnull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zamzamsuper.inventory_service.dto.BatchRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNCreateRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNResponse;
import com.zamzamsuper.inventory_service.dto.grn.GRNUpdateRequest;
import com.zamzamsuper.inventory_service.enums.BatchStatus;
import com.zamzamsuper.inventory_service.enums.GRNStatus;
import com.zamzamsuper.inventory_service.exception.FinancialMismatchException;
import com.zamzamsuper.inventory_service.exception.InvalidGRNStatusException;
import com.zamzamsuper.inventory_service.exception.StockConflictException;
import com.zamzamsuper.inventory_service.mapper.BatchMapper;
import com.zamzamsuper.inventory_service.mapper.GRNMapper;
import com.zamzamsuper.inventory_service.mapper.ProductPriceMapper;
import com.zamzamsuper.inventory_service.model.Batch;
import com.zamzamsuper.inventory_service.model.GRN;
import com.zamzamsuper.inventory_service.model.Location;
import com.zamzamsuper.inventory_service.model.ProductPrice;
import com.zamzamsuper.inventory_service.model.Stock;
import com.zamzamsuper.inventory_service.model.Supplier;
import com.zamzamsuper.inventory_service.repository.BatchRepository;
import com.zamzamsuper.inventory_service.repository.GRNRepository;
import com.zamzamsuper.inventory_service.repository.LocationRepository;
import com.zamzamsuper.inventory_service.repository.StockRepository;
import com.zamzamsuper.inventory_service.repository.SupplierRepository;
import com.zamzamsuper.inventory_service.repository.specification.GRNSpecification;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GRNService {

    private final GRNMapper grnMapper;
    private final BatchMapper batchMapper;
    private final ProductPriceMapper priceMapper;
    private final GRNRepository grnRepository;
    private final StockRepository stockRepository;
    private final BatchRepository batchRepository;
    private final SupplierRepository supplierRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public GRNResponse createGRN(@Nonnull GRNCreateRequest request) {
        log.info("Starting GRN creation for Invoice: {}", request.invoiceNum());

        // Fetch Supplier
        Supplier supplier =
                supplierRepository
                        .findById(request.supplierId())
                        .orElseThrow(() -> new EntityNotFoundException("Supplier not found"));

        // Financial Verification (batch list cannot be null)
        validateFinancials(request);

        // Map Header
        GRN grn = grnMapper.toEntity(request);
        grn.setSupplier(supplier);

        // Process Batches in Memory first
        for (BatchRequest bReq : request.batches()) {
            Stock stock = getOrCreateStock(bReq.productSku(), bReq.locationId());

            // Atomic Update in DB (Avoids Race Conditions)
            stockRepository.incrementQuantity(stock.getId(), bReq.quantity());

            // Process associated batches entity and link to GRN
            Batch batch = batchMapper.toEntity(bReq);
            batch.setStock(stock);
            grn.addBatch(batch);

            // Process associated product prices and link to Batch
            bReq.prices().forEach(
                    pReq -> {
                        ProductPrice price = priceMapper.toEntity(pReq);
                        batch.addPrice(price);
                    });
        }

        // One single Save
        GRN savedGrn = grnRepository.save(grn);

        return grnMapper.toResponse(savedGrn);
    }

    // Paginated root query + batched association loading pattern
    @Transactional(readOnly = true)
    public Page<GRNResponse> getAllGRNs(
            String invoiceNum,
            Long supplierId,
            GRNStatus status,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {
        // Get Paged IDs with Filters
        Specification<GRN> spec =
                GRNSpecification.withFilters(invoiceNum, supplierId, status, startDate, endDate);
        Page<GRN> grnPage = grnRepository.findAll(spec, pageable);

        if (grnPage.isEmpty()) return Page.empty();

        List<Long> grnIds = grnPage.getContent().stream().map(GRN::getId).toList();

        // Fetch GRNs + Batches (1st Level Join)
        List<GRN> grnWithBatches = grnRepository.findAllByIdWithBatches(grnIds);

        // Fetch Batches + Prices (2nd Level Join)
        List<Long> batchIds =
                grnWithBatches.stream()
                        .flatMap(g -> g.getBatches().stream())
                        .map(Batch::getId)
                        .toList();

        if (!batchIds.isEmpty()) {
            batchRepository.findAllWithPricesByIds(batchIds);
            // Hibernate automatically links these prices to the batches
            // already present in the Persistence Context (L1 Cache).
        }

        // Map to DTOs
        List<GRNResponse> listOfDto =
                grnWithBatches.stream().map(grnMapper::toResponse).collect(Collectors.toList());

        return new PageImpl<>(listOfDto, pageable, grnPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public GRNResponse getGRNById(Long id) {
        log.info("Fetching GRN ID: {} with optimized joins", id);
        GRN grn =
                grnRepository
                        .findByIdWithBatches(id)
                        .orElseThrow(
                                () -> new EntityNotFoundException("GRN not found with id " + id));
        return grnMapper.toResponse(grn);
    }

    @Transactional
    public GRNResponse updateGRN(Long id, @Nonnull GRNUpdateRequest request) {
        log.info("Updating GRN | ID: {}, Invoice: {}", id, request.invoiceNum());

        // Fetch existing record with details
        GRN existingGrn =
                grnRepository
                        .findByIdWithBatches(id)
                        .orElseThrow(
                                () -> new EntityNotFoundException("GRN not found with id " + id));

        // Business Rule: Block updates on Canceled records
        if (existingGrn.getStatus() == GRNStatus.CANCELLED) {
            throw new InvalidGRNStatusException("Cannot update a cancelled GRN");
        }

        // Update Supplier if changed
        if (!existingGrn.getSupplier().getId().equals(request.supplierId())) {
            Supplier supplier =
                    supplierRepository
                            .findById(request.supplierId())
                            .orElseThrow(() -> new EntityNotFoundException("Supplier not found"));
            existingGrn.setSupplier(supplier);
        }

        // Update non-critical fields
        existingGrn.setInvoiceNum(request.invoiceNum());

        // Financial Logic: Handle Discount changes
        // We trust the calculated subtotal from the existing batches
        BigDecimal subTotal = existingGrn.getSubTotalAmount();
        BigDecimal newDiscount =
                request.totalDiscount() != null ? request.totalDiscount() : BigDecimal.ZERO;

        if (newDiscount.compareTo(subTotal) > 0) {
            throw new FinancialMismatchException("Discount cannot exceed subtotal");
        }

        existingGrn.setTotalDiscount(newDiscount);
        existingGrn.setGrandTotalAmount(subTotal.subtract(newDiscount));

        log.debug("GRN Header updated | New GrandTotal: {}", existingGrn.getGrandTotalAmount());

        return grnMapper.toResponse(existingGrn);
    }

    @Transactional
    public GRNResponse cancelGRN(Long id) {

        log.info("Starting GRN cancellation process | GRN_ID={}", id);

        GRN grn =
                grnRepository
                        .findByIdWithBatches(id)
                        .orElseThrow(
                                () -> {
                                    log.error("GRN not found | GRN_ID={}", id);
                                    return new EntityNotFoundException("GRN not found");
                                });

        log.debug(
                "Fetched GRN | ID={}, Status={}, BatchCount={}",
                grn.getId(),
                grn.getStatus(),
                grn.getBatches().size());

        // Status checks
        if (grn.getStatus() == GRNStatus.CANCELLED) {
            log.warn("GRN already cancelled | GRN_ID={}", id);
            throw new InvalidGRNStatusException("GRN already cancelled");
        }

        // Process each batch
        for (Batch batch : grn.getBatches()) {

            if (batch.getStatus() == BatchStatus.CANCELLED) {
                log.info("Skipping batch {} as it is already cancelled", batch.getId());
                continue; // Move to the next batch in the loop
            }

            log.debug(
                    "Processing batch | Batch_ID={}, Stock_ID={}, SKU={}, Qty={}",
                    batch.getId(),
                    batch.getStock().getId(),
                    batch.getStock().getProductSku(),
                    batch.getQuantity());

            int updated =
                    stockRepository.decrementQuantity(
                            batch.getStock().getId(), batch.getQuantity());

            if (updated == 0) {
                log.error(
                        "Stock conflict detected | Stock_ID={}, SKU={}, RequiredQty={}",
                        batch.getStock().getId(),
                        batch.getStock().getProductSku(),
                        batch.getQuantity());

                throw new StockConflictException(batch.getStock().getProductSku());
            }

            log.debug(
                    "Stock reverted successfully | Stock_ID={}, DeductedQty={}",
                    batch.getStock().getId(),
                    batch.getQuantity());

            // Soft delete batch + prices
            batch.setStatus(BatchStatus.CANCELLED);

            batch.getPrices()
                    .forEach(
                            price -> {
                                price.setActive(false);
                                log.trace("Price disabled | Price_ID={}", price.getId());
                            });

        }

        // Soft delete GRN
        grn.setStatus(GRNStatus.CANCELLED);

        log.info("GRN cancellation completed successfully | GRN_ID={}", id);

        return grnMapper.toResponse(grn);
    }

    @Transactional
    public void purgeCancelledGRN(Long id) {
        // Fetch the GRN
        GRN grn =
                grnRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("GRN not found"));

        // SAFETY CHECK: Only CANCELLED GRNs can be purged
        if (grn.getStatus() != GRNStatus.CANCELLED) {
            log.error(
                    "Security violation: Attempted to purge a non-cancelled GRN | ID: {} | Status:"
                            + " {}",
                    id,
                    grn.getStatus());
            throw new InvalidGRNStatusException(
                    "Only CANCELLED GRNs can be permanently deleted. Cancel the GRN first.");
        }

        // 3. Execution
        // Since we have CascadeType.ALL on batches and prices,
        // removing the parent will wipe the entire tree from the DB.
        grnRepository.delete(grn);

        log.info(
                "GRN ID: {} and all associated batches/prices have been permanently purged from the"
                        + " database.",
                id);
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private void validateFinancials(@Nonnull GRNCreateRequest request) {
        BigDecimal calculatedSubTotal =
                request.batches().stream()
                        .map(b -> b.costPrice().multiply(b.quantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (calculatedSubTotal.compareTo(request.subTotalAmount()) != 0) {
            throw new FinancialMismatchException(
                    String.format(
                            "Subtotal mismatch! Calculated: %s, Provided: %s",
                            calculatedSubTotal, request.subTotalAmount()));
        }

        BigDecimal expectedGrandTotal = calculatedSubTotal.subtract(request.totalDiscount());
        if (expectedGrandTotal.compareTo(request.grandTotalAmount()) != 0) {
            throw new FinancialMismatchException(
                    String.format(
                            "Grand Total mismatch! Expected: %s, Provided: %s",
                            expectedGrandTotal, request.grandTotalAmount()));
        }
    }

    private Stock getOrCreateStock(String productSku, Long locationId) {
        return stockRepository
                .findByProductSkuAndLocationId(productSku, locationId)
                .orElseGet(
                        () -> {
                            Location location =
                                    locationRepository
                                            .findById(locationId)
                                            .orElseThrow(
                                                    () ->
                                                            new EntityNotFoundException(
                                                                    "Location not found"));

                            return stockRepository.save(
                                    Stock.builder()
                                            .productSku(productSku)
                                            .location(location)
                                            .quantityOnHand(BigDecimal.ZERO)
                                            .build());
                        });
    }
}
