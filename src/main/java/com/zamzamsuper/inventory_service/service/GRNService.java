package com.zamzamsuper.inventory_service.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zamzamsuper.inventory_service.dto.BatchRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNCreateRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNResponse;
import com.zamzamsuper.inventory_service.dto.grn.GRNUpdateRequest;
import com.zamzamsuper.inventory_service.enums.BatchStatus;
import com.zamzamsuper.inventory_service.enums.GRNStatus;
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
    public GRNResponse createGRN(GRNCreateRequest request) {
        log.info("Starting GRN creation for Invoice: {}", request.invoiceNum());

        // 1. Fetch Supplier
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new EntityNotFoundException("Supplier not found"));

        // 2. Financial Verification
        validateFinancials(request);

        // 3. Map Header    
        GRN grn = grnMapper.toEntity(request);
        grn.setSupplier(supplier);

        // 4. Process Batches in Memory first
        for (BatchRequest bReq : request.batches()) {
            Stock stock = getOrCreateStock(bReq.productSku(), bReq.locationId());

            // Atomic Update in DB (Avoids Race Conditions)
            stockRepository.incrementQuantity(stock.getId(), bReq.quantity());

            // Process associated batchs entity and link to GRN
            Batch batch = batchMapper.toEntity(bReq);
            batch.setStock(stock);
            grn.addBatch(batch); 

            // Process associated product prices and link to Batch
            bReq.prices().forEach(pReq -> {
                ProductPrice price = priceMapper.toEntity(pReq);
                batch.addPrice(price);
            });
        }

        // 5. One single Save
        GRN savedGrn = grnRepository.save(grn);
        
        return grnMapper.toResponse(savedGrn);
    }


    @Transactional(readOnly = true)
    public List<GRNResponse> getAllGRNs() {
        log.info("Fetching all GRNs with optimized joins");
        // Use the new repository method instead of findAll()
        return grnRepository.findAllWithBatchesAndPrices().stream()
                .map(grnMapper::toResponse)
                .collect(Collectors.toList());
    }

    // Paginated root query + batched association loading pattern
    @Transactional(readOnly = true)
    public Page<GRNResponse> getAllGRNs(Pageable pageable) {
        // Step 1: Get the Page of Headers (Paged)
        Page<GRN> grnPage = grnRepository.findAll(pageable);
        if (grnPage.isEmpty()) return Page.empty();

        List<Long> grnIds = grnPage.getContent().stream().map(GRN::getId).toList();

        // Step 2: Fetch GRNs + Batches (1st Level Join)
        List<GRN> grnsWithBatches = grnRepository.findAllWithBatchesByIds(grnIds);

        // Step 3: Fetch Batches + Prices (2nd Level Join)
        List<Long> batchIds = grnsWithBatches.stream()
                .flatMap(g -> g.getBatches().stream())
                .map(Batch::getId)
                .toList();
        
        if (!batchIds.isEmpty()) {
            batchRepository.findAllWithPricesByIds(batchIds);
            // Hibernate automatically links these prices to the batches 
            // already present in the Persistence Context (L1 Cache).
        }

        // Step 4: Map to DTOs
        List<GRNResponse> dtos = grnsWithBatches.stream()
                .map(grnMapper::toResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, grnPage.getTotalElements());
    }

    // @Transactional(readOnly = true)
    // public Page<GRNResponse> getAllGRNs(Pageable pageable) {
    //     log.info("Fetching paged GRNs: Page {}, Size {}", pageable.getPageNumber(), pageable.getPageSize());

    //     // 1. Get the page of GRN headers (SQL: SELECT ... LIMIT 10 OFFSET 0)
    //     Page<GRN> grnPage = grnRepository.findAll(pageable);
        
    //     if (grnPage.isEmpty()) {
    //         return Page.empty();
    //     }

    //     // 2. Extract IDs and fetch everything (Batches/Prices) in one query
    //     List<Long> ids = grnPage.getContent().stream().map(GRN::getId).toList();
    //     List<GRN> detailedGrns = grnRepository.findAllWithDetailsByIds(ids);

    //     // 3. Map to DTOs and return as a Page
    //     List<GRNResponse> dtos = detailedGrns.stream()
    //             .map(grnMapper::toResponse)
    //             .collect(Collectors.toList());

    //     return new PageImpl<>(dtos, pageable, grnPage.getTotalElements());
    // }

    @Transactional(readOnly = true)
    public GRNResponse getGRNById(Long id) {
        log.info("Fetching GRN ID: {} with optimized joins", id);
        GRN grn = grnRepository.findByIdWithBatches(id)
                .orElseThrow(() -> new EntityNotFoundException("GRN not found with id " + id));
        return grnMapper.toResponse(grn);
    }

    @Transactional
    public GRNResponse updateGRN(Long id, GRNUpdateRequest request) {
        log.info("Updating GRN | ID: {}, Invoice: {}", id, request.invoiceNum());

        // 1. Fetch existing record with details
        GRN existingGrn = grnRepository.findByIdWithBatches(id)
                .orElseThrow(() -> new EntityNotFoundException("GRN not found with id " + id));

        // 2. Business Rule: Block updates on Cancelled records
        if (existingGrn.getStatus() == GRNStatus.CANCELLED) {
            throw new IllegalStateException("Cannot update a cancelled GRN");
        }

        // 3. Update Supplier if changed
        if (!existingGrn.getSupplier().getId().equals(request.supplierId())) {
            Supplier supplier = supplierRepository.findById(request.supplierId())
                    .orElseThrow(() -> new EntityNotFoundException("Supplier not found"));
            existingGrn.setSupplier(supplier);
        }

        // 4. Update non-critical fields
        existingGrn.setInvoiceNum(request.invoiceNum());

        // 5. Financial Logic: Handle Discount changes
        // We trust the calculated subtotal from the existing batches
        BigDecimal subTotal = existingGrn.getSubTotalAmount();
        BigDecimal newDiscount = request.totalDiscount() != null ? request.totalDiscount() : BigDecimal.ZERO;

        if (newDiscount.compareTo(subTotal) > 0) {
            throw new IllegalArgumentException("Discount cannot exceed subtotal");
        }

        existingGrn.setTotalDiscount(newDiscount);
        existingGrn.setGrandTotalAmount(subTotal.subtract(newDiscount));

        log.debug("GRN Header updated | New GrandTotal: {}", existingGrn.getGrandTotalAmount());

        return grnMapper.toResponse(grnRepository.save(existingGrn));
    }

    /**
     * Deleting a GRN is a cascading operation.
     * We must adjust Stock levels for every batch linked to this GRN before deletion.
     */
    // @Transactional
    // public void deleteGRN(Long id) {
    //     GRN grn = grnRepository.findById(id)
    //             .orElseThrow(() -> new EntityNotFoundException("GRN not found with id " + id));

    //     // 1. Find all batches associated with this GRN
    //     List<Batch> batches = batchRepository.findByGrnId(id);

    //     for (Batch batch : batches) {
    //         // 2. Adjust Stock quantity (Deduct)
    //         Stock stock = batch.getStock();
    //         stock.setQuantityOnHand(stock.getQuantityOnHand() - batch.getQuantity());
    //         stockRepository.save(stock);
            
    //         // 3. Delete prices and batch (handled by CascadeType if configured, otherwise manual)
    //         priceRepository.deleteByBatchId(batch.getId());
    //         batchRepository.delete(batch);
    //     }

    //     // 4. Finally delete the GRN header
    //     grnRepository.delete(grn);
    // }

    @Transactional
    public GRNResponse cancelGRN(Long id) {

        log.info("Starting GRN cancellation process | GRN_ID={}", id);

        GRN grn = grnRepository.findByIdWithBatches(id)
                .orElseThrow(() -> {
                    log.error("GRN not found | GRN_ID={}", id);
                    return new EntityNotFoundException("GRN not found");
                });

        log.debug("Fetched GRN | ID={}, Status={}, BatchCount={}",
                grn.getId(), grn.getStatus(), grn.getBatches().size());

        // 1. Status checks
        if (grn.getStatus() == GRNStatus.CANCELLED) {
            log.warn("GRN already cancelled | GRN_ID={}", id);
            throw new IllegalStateException("GRN already cancelled");
        }

        // 2. Process each batch
        for (Batch batch : grn.getBatches()) {

            log.debug("Processing batch | Batch_ID={}, Stock_ID={}, SKU={}, Qty={}",
                    batch.getId(),
                    batch.getStock().getId(),
                    batch.getStock().getProductSku(),
                    batch.getQuantity());

            int updated = stockRepository.decrementStockSafely(
                    batch.getStock().getId(),
                    batch.getQuantity()
            );

            if (updated == 0) {
                log.error("Stock conflict detected | Stock_ID={}, SKU={}, RequiredQty={}",
                        batch.getStock().getId(),
                        batch.getStock().getProductSku(),
                        batch.getQuantity());

                throw new IllegalStateException(
                        "Stock conflict for SKU: " + batch.getStock().getProductSku()
                );
            }

            log.debug("Stock reverted successfully | Stock_ID={}, DeductedQty={}",
                    batch.getStock().getId(),
                    batch.getQuantity());

            // 3. Soft delete batch + prices
            batch.setStatus(BatchStatus.CANCELLED);

            if (batch.getPrices() != null) {
                batch.getPrices().forEach(price -> {
                    price.setActive(false);
                    log.trace("Price disabled | Price_ID={}", price.getId());
                });
            }
        }

        // 4. Soft delete GRN
        grn.setStatus(GRNStatus.CANCELLED);

        log.info("GRN cancellation completed successfully | GRN_ID={}", id);

        return grnMapper.toResponse(grn);
    }

    @Transactional
    public void purgeCancelledGRN(Long id) {
        // 1. Fetch the GRN
        GRN grn = grnRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("GRN not found"));

        // 2. SAFETY CHECK: Only CANCELLED GRNs can be purged
        if (grn.getStatus() != GRNStatus.CANCELLED) {
            log.error("Security violation: Attempted to purge a non-cancelled GRN | ID: {} | Status: {}", id, grn.getStatus());
            throw new IllegalStateException("Only CANCELLED GRNs can be permanently deleted. Cancel the GRN first.");
        }

        // 3. Execution
        // Since we have CascadeType.ALL on batches and prices, 
        // removing the parent will wipe the entire tree from the DB.
        grnRepository.delete(grn);
        
        log.info("GRN ID: {} and all associated batches/prices have been permanently purged from the database.", id);
    }

	private void validateFinancials(GRNCreateRequest request) {
        BigDecimal calculatedSubTotal = request.batches().stream()
                .map(b -> b.costPrice().multiply(BigDecimal.valueOf(b.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (calculatedSubTotal.compareTo(request.subTotalAmount()) != 0) {
                throw new IllegalArgumentException(String.format(
                "Subtotal mismatch! Calculated: %s, Provided: %s", 
                calculatedSubTotal, request.subTotalAmount()));
        }

        BigDecimal expectedGrandTotal = calculatedSubTotal.subtract(request.totalDiscount());
        if (expectedGrandTotal.compareTo(request.grandTotalAmount()) != 0) {
                throw new IllegalArgumentException(String.format(
                "Grand Total mismatch! Expected: %s, Provided: %s", 
                expectedGrandTotal, request.grandTotalAmount()));
        }
	}

    private Stock getOrCreateStock(String productSku, Long locationId) {
        return stockRepository.findByProductSkuAndLocationId(productSku, locationId)
                .orElseGet(() -> {
                    Location location = locationRepository.findById(locationId)
                        .orElseThrow(() -> new EntityNotFoundException("Location not found"));

                    return stockRepository.save(Stock.builder()
                            .productSku(productSku)
                            .location(location)
                            .quantityOnHand(0)
                            .reorderLevel(10)
                            .build());
                });
    }
}