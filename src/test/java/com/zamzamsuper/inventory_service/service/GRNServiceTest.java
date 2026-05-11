package com.zamzamsuper.inventory_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.zamzamsuper.inventory_service.dto.BatchRequest;
import com.zamzamsuper.inventory_service.dto.ProductPriceRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNCreateRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNResponse;
import com.zamzamsuper.inventory_service.dto.grn.GRNUpdateRequest;
import com.zamzamsuper.inventory_service.enums.BatchStatus;
import com.zamzamsuper.inventory_service.enums.GRNStatus;
import com.zamzamsuper.inventory_service.enums.PriceType;
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

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class GRNServiceTest {

    // =====================================================================
    // Mocks
    // =====================================================================

    @Mock private GRNRepository grnRepository;
    @Mock private StockRepository stockRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private GRNMapper grnMapper;
    @Mock private BatchMapper batchMapper;
    @Mock private ProductPriceMapper priceMapper;

    @InjectMocks private GRNService grnService;

    // =====================================================================
    // Shared fixtures
    // =====================================================================

    private Location mockLocation;
    private Supplier mockSupplier;
    private Stock mockStock;
    private GRN grn1, grn2;
    private Batch batch1, batch2;
    private GRNResponse response1, response2;
    private GRNCreateRequest validRequest;

    @BeforeEach
    void setUpSharedFixtures() {
        mockLocation = Location.builder().id(1L).name("Test Location").active(true).build();

        mockSupplier = Supplier.builder().id(1L).name("Test Supplier").phone("9999999999").build();

        mockStock =
                Stock.builder()
                        .id(50L)
                        .productSku("SKU-001")
                        .location(mockLocation)
                        .quantityOnHand(100)
                        .reorderLevel(10)
                        .build();

        validRequest = buildValidCreateRequest();

        grn1 =
                buildGRN(
                        1L,
                        "INV-001",
                        BigDecimal.valueOf(500),
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(500),
                        GRNStatus.POSTED,
                        LocalDateTime.now().minusDays(3));
        grn2 =
                buildGRN(
                        2L,
                        "INV-002",
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(50),
                        BigDecimal.valueOf(950),
                        GRNStatus.DRAFT,
                        LocalDateTime.now().minusDays(2));

        batch1 = buildBatch(10L, mockStock, grn1, "BATCH-001", BigDecimal.valueOf(50), 10, 6);
        batch2 = buildBatch(11L, mockStock, grn2, "BATCH-002", BigDecimal.valueOf(80), 20, 12);

        grn1.setBatches(List.of(batch1));
        grn2.setBatches(List.of(batch2));

        response1 =
                buildGRNResponse(
                        grn1,
                        "INV-001",
                        BigDecimal.valueOf(500),
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(500),
                        GRNStatus.POSTED);
        response2 =
                buildGRNResponse(
                        grn2,
                        "INV-002",
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(50),
                        BigDecimal.valueOf(950),
                        GRNStatus.DRAFT);
    }

    // =====================================================================
    // createGRN
    // =====================================================================

    @Nested
    @DisplayName("createGRN")
    class CreateGRNTests {

        /**
         * Stubs mappers + save as a plain helper, NOT @BeforeEach. Tests that throw before reaching
         * mapper/save code must NOT call this, otherwise Mockito strict mode raises
         * UnnecessaryStubbingException.
         */
        private void stubMappersAndSave() {
            GRN mappedGrn =
                    GRN.builder()
                            .supplier(mockSupplier)
                            .invoiceNum("INV-001")
                            .subTotalAmount(BigDecimal.valueOf(500))
                            .totalDiscount(BigDecimal.ZERO)
                            .grandTotalAmount(BigDecimal.valueOf(500))
                            .status(GRNStatus.POSTED)
                            .build();
            when(grnMapper.toEntity(any(GRNCreateRequest.class))).thenReturn(mappedGrn);

            Batch mappedBatch =
                    Batch.builder()
                            .batchNumber("BATCH-001")
                            .costPrice(BigDecimal.valueOf(50))
                            .quantity(10)
                            .status(BatchStatus.ACTIVE)
                            .build();
            when(batchMapper.toEntity(any(BatchRequest.class))).thenReturn(mappedBatch);
            when(priceMapper.toEntity(any(ProductPriceRequest.class)))
                    .thenReturn(new ProductPrice());
            when(grnRepository.save(any(GRN.class))).thenReturn(grn1);
        }

        @Test
        @DisplayName("Happy path — saves GRN and increments existing stock")
        void givenExistingStock_whenCreateGRN_thenGRNSavedAndStockIncremented() {
            stubMappersAndSave();
            when(supplierRepository.findById(1L)).thenReturn(Optional.of(mockSupplier));
            when(stockRepository.findByProductSkuAndLocationId(anyString(), anyLong()))
                    .thenReturn(Optional.of(mockStock));
            doNothing().when(stockRepository).incrementQuantity(anyLong(), anyInt());

            grnService.createGRN(validRequest);

            verify(stockRepository).incrementQuantity(50L, 10);

            ArgumentCaptor<GRN> captor = ArgumentCaptor.forClass(GRN.class);
            verify(grnRepository).save(captor.capture());
            GRN saved = captor.getValue();

            assertNotNull(saved);
            assertEquals("INV-001", saved.getInvoiceNum());
            assertEquals(BigDecimal.valueOf(500), saved.getSubTotalAmount());
            assertEquals(GRNStatus.POSTED, saved.getStatus());
            assertEquals(mockSupplier, saved.getSupplier());
            assertEquals(1, saved.getBatches().size());
            assertEquals("BATCH-001", saved.getBatches().get(0).getBatchNumber());
            assertEquals(10, saved.getBatches().get(0).getQuantity());
            assertEquals(BigDecimal.valueOf(50), saved.getBatches().get(0).getCostPrice());
        }

        @Test
        @DisplayName("Creates and saves new stock when none exists for the SKU/location")
        void givenNoExistingStock_whenCreateGRN_thenNewStockCreatedAndIncremented() {
            stubMappersAndSave();
            when(supplierRepository.findById(anyLong())).thenReturn(Optional.of(mockSupplier));
            when(stockRepository.findByProductSkuAndLocationId(anyString(), anyLong()))
                    .thenReturn(Optional.empty());
            when(locationRepository.findById(anyLong())).thenReturn(Optional.of(mockLocation));
            Stock newStock = Stock.builder().id(99L).productSku("SKU-001").build();
            when(stockRepository.save(any(Stock.class))).thenReturn(newStock);
            doNothing().when(stockRepository).incrementQuantity(anyLong(), anyInt());

            grnService.createGRN(validRequest);

            verify(locationRepository).findById(anyLong());
            verify(stockRepository).save(any(Stock.class));
            verify(stockRepository).incrementQuantity(99L, 10);
        }

        @Test
        @DisplayName("Throws EntityNotFoundException when supplier does not exist")
        void givenMissingSupplier_whenCreateGRN_thenThrows() {
            // Throws before any mapper is reached — no mapper stubs needed.
            when(supplierRepository.findById(anyLong())).thenReturn(Optional.empty());

            EntityNotFoundException ex =
                    assertThrows(
                            EntityNotFoundException.class,
                            () -> grnService.createGRN(validRequest));

            assertEquals("Supplier not found", ex.getMessage());
        }

        @Test
        @DisplayName("Throws EntityNotFoundException when location is missing for new stock")
        void givenMissingLocation_whenCreateGRN_thenThrows() {
            when(supplierRepository.findById(anyLong())).thenReturn(Optional.of(mockSupplier));
            when(stockRepository.findByProductSkuAndLocationId(anyString(), anyLong()))
                    .thenReturn(Optional.empty());
            when(locationRepository.findById(anyLong())).thenReturn(Optional.empty());
            // The location lookup happens inside the batch loop, after GRN is mapped,
            // so grnMapper.toEntity is needed but not the batch/price mappers or save.
            GRN mappedGrn =
                    GRN.builder()
                            .supplier(mockSupplier)
                            .invoiceNum("INV-001")
                            .subTotalAmount(BigDecimal.valueOf(500))
                            .totalDiscount(BigDecimal.ZERO)
                            .grandTotalAmount(BigDecimal.valueOf(500))
                            .status(GRNStatus.POSTED)
                            .build();
            when(grnMapper.toEntity(any(GRNCreateRequest.class))).thenReturn(mappedGrn);

            EntityNotFoundException ex =
                    assertThrows(
                            EntityNotFoundException.class,
                            () -> grnService.createGRN(validRequest));

            assertEquals("Location not found", ex.getMessage());
        }

        @Test
        @DisplayName("Throws FinancialMismatchException when subtotal does not match batch sum")
        void givenSubtotalMismatch_whenCreateGRN_thenThrows() {
            // Financial validation runs before mappers — no mapper stubs needed.
            when(supplierRepository.findById(anyLong())).thenReturn(Optional.of(mockSupplier));

            FinancialMismatchException ex =
                    assertThrows(
                            FinancialMismatchException.class,
                            () ->
                                    grnService.createGRN(
                                            withAmounts(
                                                    BigDecimal.valueOf(600),
                                                    BigDecimal.ZERO,
                                                    BigDecimal.valueOf(600))));

            assertEquals("Subtotal mismatch! Calculated: 500, Provided: 600", ex.getMessage());
        }

        @Test
        @DisplayName(
                "Throws FinancialMismatchException when grand total does not match subtotal minus"
                        + " discount")
        void givenGrandTotalMismatch_whenCreateGRN_thenThrows() {
            // Financial validation runs before mappers — no mapper stubs needed.
            when(supplierRepository.findById(anyLong())).thenReturn(Optional.of(mockSupplier));

            FinancialMismatchException ex =
                    assertThrows(
                            FinancialMismatchException.class,
                            () ->
                                    grnService.createGRN(
                                            withAmounts(
                                                    BigDecimal.valueOf(500),
                                                    BigDecimal.valueOf(30),
                                                    BigDecimal.valueOf(450))));

            assertEquals("Grand Total mismatch! Expected: 470, Provided: 450", ex.getMessage());
        }
    }

    // =====================================================================
    // getAllGRNs
    // =====================================================================

    @Nested
    @DisplayName("getAllGRNs")
    class GetAllGRNsTests {

        @Test
        @DisplayName("Returns paged responses using split-fetch hydration")
        void givenGRNsExist_whenGetAll_thenReturnsHydratedPage() {
            Pageable pageable = PageRequest.of(0, 2);
            Page<GRN> grnPage = new PageImpl<>(List.of(grn1, grn2), pageable, 3);

            when(grnRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(grnPage);
            when(grnRepository.findAllWithBatchesByIds(List.of(1L, 2L)))
                    .thenReturn(List.of(grn1, grn2));
            when(batchRepository.findAllWithPricesByIds(List.of(10L, 11L)))
                    .thenReturn(List.of(batch1, batch2));
            when(grnMapper.toResponse(grn1)).thenReturn(response1);
            when(grnMapper.toResponse(grn2)).thenReturn(response2);

            Page<GRNResponse> result =
                    grnService.getAllGRNs(null, null, null, null, null, pageable);

            assertNotNull(result);
            assertEquals(2, result.getContent().size());
            assertEquals(3, result.getTotalElements());
            verify(grnRepository).findAll(any(Specification.class), eq(pageable));
            verify(batchRepository).findAllWithPricesByIds(anyList());
        }
    }

    // =====================================================================
    // updateGRN
    // =====================================================================

    @Nested
    @DisplayName("updateGRN")
    class UpdateGRNTests {

        /**
         * The update test applies a discount of 100 and expects grandTotal=900, so this GRN needs
         * subTotal=1000. The shared grn1 has subTotal=500 (which would give 400), so we build a
         * dedicated fixture here.
         */
        private GRN grnForUpdate;

        @BeforeEach
        void setUpUpdateFixture() {
            grnForUpdate =
                    buildGRN(
                            1L,
                            "INV-001",
                            BigDecimal.valueOf(1000),
                            BigDecimal.ZERO,
                            BigDecimal.valueOf(1000),
                            GRNStatus.POSTED,
                            LocalDateTime.now().minusDays(3));
        }

        @Test
        @DisplayName("Updates invoice number and recalculates grand total")
        void givenValidRequest_whenUpdate_thenHeaderAndTotalUpdated() {
            when(grnRepository.findByIdWithBatches(grnForUpdate.getId()))
                    .thenReturn(Optional.of(grnForUpdate));
            when(grnRepository.save(any(GRN.class))).thenAnswer(inv -> inv.getArgument(0));

            grnService.updateGRN(
                    grnForUpdate.getId(),
                    new GRNUpdateRequest(grnForUpdate.getId(), "NEW-INV", BigDecimal.valueOf(100)));

            assertEquals("NEW-INV", grnForUpdate.getInvoiceNum());
            assertEquals(BigDecimal.valueOf(900), grnForUpdate.getGrandTotalAmount()); // 1000 - 100
        }

        @Test
        @DisplayName("Throws InvalidGRNStatusException when GRN is already cancelled")
        void givenCancelledGRN_whenUpdate_thenThrows() {
            GRN cancelled = GRN.builder().status(GRNStatus.CANCELLED).build();
            when(grnRepository.findByIdWithBatches(1L)).thenReturn(Optional.of(cancelled));

            assertThrows(
                    InvalidGRNStatusException.class,
                    () ->
                            grnService.updateGRN(
                                    1L, new GRNUpdateRequest(1L, "INV", BigDecimal.ZERO)));
        }
    }

    // =====================================================================
    // cancelGRN
    // =====================================================================

    @Nested
    @DisplayName("cancelGRN")
    class CancelGRNTests {

        @Test
        @DisplayName("Reverts stock and marks GRN and its batches as CANCELLED")
        void givenPostedGRN_whenCancel_thenStockRevertedAndStatusUpdated() {
            when(grnRepository.findByIdWithBatches(grn1.getId())).thenReturn(Optional.of(grn1));
            when(stockRepository.decrementStockSafely(
                            batch1.getStock().getId(), batch1.getQuantity()))
                    .thenReturn(1);

            grnService.cancelGRN(grn1.getId());

            assertEquals(GRNStatus.CANCELLED, grn1.getStatus());
            assertEquals(BatchStatus.CANCELLED, batch1.getStatus());
            verify(stockRepository).decrementStockSafely(50L, 10);
        }

        @Test
        @DisplayName("Throws StockConflictException when stock cannot be safely decremented")
        void givenUtilizedStock_whenCancel_thenThrowsStockConflict() {
            batch1.setQuantity(110);
            when(grnRepository.findByIdWithBatches(grn1.getId())).thenReturn(Optional.of(grn1));
            when(stockRepository.decrementStockSafely(
                            batch1.getStock().getId(), batch1.getQuantity()))
                    .thenReturn(0);

            assertThrows(StockConflictException.class, () -> grnService.cancelGRN(grn1.getId()));
        }
    }

    // =====================================================================
    // purgeCancelledGRN
    // =====================================================================

    @Nested
    @DisplayName("purgeCancelledGRN")
    class PurgeTests {

        @Test
        @DisplayName("Deletes GRN from repository when status is CANCELLED")
        void givenCancelledGRN_whenPurge_thenDeleted() {
            grn1.setStatus(GRNStatus.CANCELLED);
            when(grnRepository.findById(grn1.getId())).thenReturn(Optional.of(grn1));
            doNothing().when(grnRepository).delete(grn1);

            grnService.purgeCancelledGRN(grn1.getId());

            verify(grnRepository).delete(grn1);
        }

        @Test
        @DisplayName("Throws InvalidGRNStatusException when GRN is not CANCELLED")
        void givenPostedGRN_whenPurge_thenThrows() {
            grn1.setStatus(GRNStatus.POSTED);
            when(grnRepository.findById(grn1.getId())).thenReturn(Optional.of(grn1));

            assertThrows(
                    InvalidGRNStatusException.class,
                    () -> grnService.purgeCancelledGRN(grn1.getId()));
        }
    }

    // =====================================================================
    // Builder helpers
    // =====================================================================

    private GRNCreateRequest buildValidCreateRequest() {
        ProductPriceRequest priceRequest =
                new ProductPriceRequest(
                        null,
                        PriceType.RETAIL,
                        true,
                        1,
                        BigDecimal.valueOf(75),
                        BigDecimal.valueOf(70));

        BatchRequest batchRequest =
                new BatchRequest(
                        null,
                        "SKU-001",
                        1L,
                        LocalDate.now().plusMonths(6),
                        "BATCH-001",
                        BigDecimal.valueOf(50),
                        10,
                        BatchStatus.ACTIVE,
                        List.of(priceRequest));

        return new GRNCreateRequest(
                1L,
                "INV-001",
                BigDecimal.valueOf(500),
                BigDecimal.ZERO,
                BigDecimal.valueOf(500),
                GRNStatus.POSTED,
                List.of(batchRequest));
    }

    private GRNCreateRequest withAmounts(
            BigDecimal subTotal, BigDecimal discount, BigDecimal grandTotal) {
        return new GRNCreateRequest(
                1L,
                "INV-001",
                subTotal,
                discount,
                grandTotal,
                GRNStatus.POSTED,
                validRequest.batches());
    }

    private GRN buildGRN(
            Long id,
            String invoiceNum,
            BigDecimal subTotal,
            BigDecimal discount,
            BigDecimal grandTotal,
            GRNStatus status,
            LocalDateTime createdAt) {
        return GRN.builder()
                .id(id)
                .supplier(mockSupplier)
                .invoiceNum(invoiceNum)
                .subTotalAmount(subTotal)
                .totalDiscount(discount)
                .grandTotalAmount(grandTotal)
                .status(status)
                .createdAt(createdAt)
                .build();
    }

    private Batch buildBatch(
            Long id,
            Stock stock,
            GRN grn,
            String batchNumber,
            BigDecimal costPrice,
            int quantity,
            int expiryMonths) {
        return Batch.builder()
                .id(id)
                .stock(stock)
                .grn(grn)
                .batchNumber(batchNumber)
                .costPrice(costPrice)
                .quantity(quantity)
                .status(BatchStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusMonths(expiryMonths))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    private GRNResponse buildGRNResponse(
            GRN grn,
            String invoiceNum,
            BigDecimal subTotal,
            BigDecimal discount,
            BigDecimal grandTotal,
            GRNStatus status) {
        return new GRNResponse(
                grn.getId(),
                mockSupplier.getId(),
                mockSupplier.getName(),
                invoiceNum,
                subTotal,
                discount,
                grandTotal,
                status,
                List.of(),
                grn.getCreatedAt(),
                null);
    }
}
