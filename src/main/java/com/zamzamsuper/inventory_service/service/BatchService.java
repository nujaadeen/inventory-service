package com.zamzamsuper.inventory_service.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zamzamsuper.inventory_service.dto.BatchRequest;
import com.zamzamsuper.inventory_service.dto.BatchResponse;
import com.zamzamsuper.inventory_service.dto.ProductPriceRequest;
import com.zamzamsuper.inventory_service.mapper.BatchMapper;
import com.zamzamsuper.inventory_service.mapper.ProductPriceMapper;
import com.zamzamsuper.inventory_service.model.Batch;
import com.zamzamsuper.inventory_service.model.GRN;
import com.zamzamsuper.inventory_service.model.ProductPrice;
import com.zamzamsuper.inventory_service.model.Stock;
import com.zamzamsuper.inventory_service.repository.BatchRepository;
import com.zamzamsuper.inventory_service.repository.GRNRepository;
import com.zamzamsuper.inventory_service.repository.ProductPriceRepository;
import com.zamzamsuper.inventory_service.repository.StockRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchMapper batchMapper;
    private final ProductPriceMapper priceMapper;
    private final BatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final GRNRepository grnRepository;
    private final ProductPriceRepository priceRepository;

    @Transactional
    public BatchResponse createBatch(BatchRequest request) {
        if (request.grnId() == null) {
            throw new IllegalArgumentException("GRN ID is required for batch creation");
        }

        GRN grn =
                grnRepository
                        .findById(request.grnId())
                        .orElseThrow(
                                () ->
                                        new EntityNotFoundException(
                                                "GRN not found with ID: " + request.grnId()));

        // Logic: Find or create stock master record
        Stock stock =
                stockRepository
                        .findByProductSkuAndLocationId(request.productSku(), request.locationId())
                        .orElseThrow(
                                () ->
                                        new EntityNotFoundException(
                                                "Stock not found with SKU: "
                                                        + request.productSku()
                                                        + " and Location ID: "
                                                        + request.locationId()));

        // Update Stock Total
        stock.setQuantityOnHand(stock.getQuantityOnHand().add(request.quantity()));
        stockRepository.save(stock);

        Batch batch =
                Batch.builder()
                        .grn(grn)
                        .stock(stock)
                        .batchNumber(request.batchNumber())
                        .costPrice(request.costPrice())
                        .quantity(request.quantity())
                        .expiryDate(request.expiryDate())
                        .build();

        Batch savedBatch = batchRepository.save(batch);

        // 3. Save Product Prices
        if (request.prices() != null) {
            // Save prices and capture the list
            List<ProductPrice> savedPrices = saveProductPrices(savedBatch, request.prices());

            // CRITICAL: Manually link the saved prices to the entity in memory
            savedBatch.setPrices(savedPrices);
        }

        // 3. Automatically Recompute GRN Totals
        updateGrnTotals(grn);

        return batchMapper.toResponse(savedBatch);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getAllBatches() {
        return batchRepository.findAll().stream()
                .map(batchMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BatchResponse getBatchById(Long id) {
        return batchRepository
                .findById(id)
                .map(batchMapper::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Batch not found with ID: " + id));
    }

    @Transactional
    public BatchResponse updateBatch(Long id, BatchRequest request) {
        Batch existingBatch =
                batchRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new EntityNotFoundException(
                                                "Batch not found with ID: " + id));

        // CRITICAL: Adjust Stock quantity if the Batch quantity changed
        if (!existingBatch.getQuantity().equals(request.quantity())) {
            BigDecimal difference = request.quantity().subtract(existingBatch.getQuantity());
            Stock stock = existingBatch.getStock();
            stock.setQuantityOnHand(stock.getQuantityOnHand().add(difference));
            stockRepository.save(stock);
        }

        existingBatch.setBatchNumber(request.batchNumber());
        existingBatch.setExpiryDate(request.expiryDate());
        existingBatch.setCostPrice(request.costPrice());
        existingBatch.setQuantity(request.quantity());

        Batch savedBatch = batchRepository.save(existingBatch);

        // 3. Update Prices (Delete old ones and save new ones for simplicity, or implement sync
        // logic)
        if (request.prices() != null) {
            priceRepository.deleteByBatchId(id);
            // Save prices and capture the list
            List<ProductPrice> savedPrices = saveProductPrices(savedBatch, request.prices());

            // CRITICAL: Manually link the saved prices to the entity in memory
            savedBatch.setPrices(savedPrices);
        }

        // 3. Automatically Recompute GRN Totals
        updateGrnTotals(existingBatch.getGrn());

        return batchMapper.toResponse(savedBatch);
    }

    @Transactional
    public void deleteBatch(Long id) {
        Batch batch =
                batchRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Batch not found"));

        // CRITICAL: Deduct quantity from stock before deleting the batch
        Stock stock = batch.getStock();
        stock.setQuantityOnHand(stock.getQuantityOnHand().subtract(batch.getQuantity()));
        stockRepository.save(stock);

        batchRepository.delete(batch);

        updateGrnTotals(batch.getGrn());
    }

    /**
     * Recalculates the Subtotal and Grand Total for a GRN. Formula: Subtotal = Sum of all (Batch
     * Quantity * Batch CostPrice) GrandTotal = Subtotal - TotalDiscount
     */
    private void updateGrnTotals(GRN grn) {
        // We fetch the latest batches from the DB to ensure accuracy
        List<Batch> currentBatches = batchRepository.findByGrnId(grn.getId());

        BigDecimal newSubTotal =
                currentBatches.stream()
                        .map(b -> b.getCostPrice().multiply(b.getQuantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        grn.setSubTotalAmount(newSubTotal);

        // Calculate Grand Total based on existing discount
        BigDecimal discount =
                grn.getTotalDiscount() != null ? grn.getTotalDiscount() : BigDecimal.ZERO;
        grn.setGrandTotalAmount(newSubTotal.subtract(discount));

        grnRepository.save(grn);
    }

    // --- Helper for Saving Prices ---
    private List<ProductPrice> saveProductPrices(
            Batch batch, List<ProductPriceRequest> priceRequests) {
        List<ProductPrice> prices =
                priceRequests.stream().map(priceMapper::toEntity).collect(Collectors.toList());
        prices.forEach(batch::addPrice);
        return priceRepository.saveAll(prices);
    }
}
