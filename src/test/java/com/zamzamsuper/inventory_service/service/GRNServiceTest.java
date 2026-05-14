package com.zamzamsuper.inventory_service.service;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.zamzamsuper.inventory_service.dto.grn.GRNUpdateRequest;
import com.zamzamsuper.inventory_service.exception.InvalidGRNStatusException;
import com.zamzamsuper.inventory_service.exception.StockConflictException;
import com.zamzamsuper.inventory_service.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zamzamsuper.inventory_service.dto.BatchRequest;
import com.zamzamsuper.inventory_service.dto.ProductPriceRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNCreateRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNResponse;
import com.zamzamsuper.inventory_service.enums.BatchStatus;
import com.zamzamsuper.inventory_service.enums.GRNStatus;
import com.zamzamsuper.inventory_service.enums.PriceType;
import com.zamzamsuper.inventory_service.exception.FinancialMismatchException;
import com.zamzamsuper.inventory_service.mapper.BatchMapper;
import com.zamzamsuper.inventory_service.mapper.GRNMapper;
import com.zamzamsuper.inventory_service.mapper.ProductPriceMapper;
import com.zamzamsuper.inventory_service.model.Batch;
import com.zamzamsuper.inventory_service.model.GRN;
import com.zamzamsuper.inventory_service.model.Location;
import com.zamzamsuper.inventory_service.model.ProductPrice;
import com.zamzamsuper.inventory_service.model.Stock;
import com.zamzamsuper.inventory_service.model.Supplier;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class GRNServiceTest {

    @Mock private GRNMapper grnMapper;
    @Mock private BatchMapper batchMapper;
    @Mock private ProductPriceMapper priceMapper;
    @Mock private GRNRepository grnRepository;
    @Mock private BatchRepository batchRepository;
    @Mock private StockRepository stockRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private LocationRepository locationRepository;

    @InjectMocks
    private GRNService grnService;

    private Supplier supplier;
    private Location location;
    private Stock stock;

    @BeforeEach
    void setUp() {
        supplier = Supplier.builder().id(1L).name("Test Supplier").build();
        location = Location.builder().id(10L).name("Main Warehouse").build();
        stock = Stock.builder().id(100L).productSku("SKU-123").location(location).build();
    }

    @Nested
    @DisplayName("Create GRN Tests")
    class CreateGRNTests {

        // =====================================================================
        // Successful creation scenarios:
        // =====================================================================

        @Test
        @DisplayName("Should create GRN successfully with existing stock")
        void createGRN_WithExistingStock_Success() {
            // Given
            GRNCreateRequest request = buildValidRequest();
            GRN mappedGrn = new GRN();
            Batch mappedBatch = new Batch();
            ProductPrice mappedPrice = new ProductPrice();
            GRNResponse expectedResponse = mock(GRNResponse.class);

            when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
            when(grnMapper.toEntity(request)).thenReturn(mappedGrn);

            // Stock exists in DB
            when(stockRepository.findByProductSkuAndLocationId("SKU-123", 10L))
                    .thenReturn(Optional.of(stock));

            when(batchMapper.toEntity(any(BatchRequest.class))).thenReturn(mappedBatch);
            when(priceMapper.toEntity(any(ProductPriceRequest.class))).thenReturn(mappedPrice);
            when(grnRepository.save(mappedGrn)).thenReturn(mappedGrn);
            when(grnMapper.toResponse(mappedGrn)).thenReturn(expectedResponse);

            // When
            GRNResponse response = grnService.createGRN(request);

            // Then
            assertThat(response).isEqualTo(expectedResponse);

            // Verify Stock was incremented
            verify(stockRepository).incrementQuantity(100L, BigDecimal.valueOf(50));
            // Verify new stock was NOT created
            verify(stockRepository, never()).save(any(Stock.class));
            // Verify parent-child relationships were linked
            assertThat(mappedGrn.getBatches()).contains(mappedBatch);
            assertThat(mappedBatch.getStock()).isEqualTo(stock);
            assertThat(mappedBatch.getPrices()).contains(mappedPrice);
        }

        @Test
        @DisplayName("Should create GRN successfully and generate new stock if it does not exist")
        void createGRN_WithNewStock_Success() {
            // Given
            GRNCreateRequest request = buildValidRequest();
            GRN mappedGrn = new GRN();
            Batch mappedBatch = new Batch();
            ProductPrice mappedPrice = new ProductPrice();
            GRNResponse expectedResponse = mock(GRNResponse.class);
            Stock newStock = Stock.builder().id(101L).productSku("SKU-123").build(); // Emulate saved stock

            when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
            when(grnMapper.toEntity(request)).thenReturn(mappedGrn);

            // Stock DOES NOT exist
            when(stockRepository.findByProductSkuAndLocationId("SKU-123", 10L)).thenReturn(Optional.empty());
            when(locationRepository.findById(10L)).thenReturn(Optional.of(location));
            when(stockRepository.save(any(Stock.class))).thenReturn(newStock);

            when(batchMapper.toEntity(any(BatchRequest.class))).thenReturn(mappedBatch);
            when(priceMapper.toEntity(any(ProductPriceRequest.class))).thenReturn(mappedPrice);
            when(grnRepository.save(mappedGrn)).thenReturn(mappedGrn);
            when(grnMapper.toResponse(mappedGrn)).thenReturn(expectedResponse);

            // When
            GRNResponse response = grnService.createGRN(request);

            // Then
            assertThat(response).isEqualTo(expectedResponse);

            // verify new stock creation flow was executed
            verify(locationRepository).findById(10L);
            verify(stockRepository).save(any(Stock.class));
            // Verify increment on the newly generated stock ID
            verify(stockRepository).incrementQuantity(101L, BigDecimal.valueOf(50));
        }

        // =====================================================================
        // Failed creation scenarios:
        // =====================================================================

        @Test
        @DisplayName("Should throw EntityNotFoundException when Supplier does not exist")
        void createGRN_WhenSupplierNotFound_ThrowsException() {
            // Given
            GRNCreateRequest request = buildValidRequest();
            when(supplierRepository.findById(1L)).thenReturn(Optional.empty());

            // When
            Throwable thrown = catchThrowable(() -> grnService.createGRN(request));

            // Then
            assertThat(thrown)
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Supplier not found");

            verify(grnRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when creating new stock and Location does not exist")
        void createGRN_WhenLocationNotFoundForNewStock_ThrowsException() {
            // Given
            GRNCreateRequest request = buildValidRequest();
            GRN mappedGrn = new GRN();

            when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
            when(grnMapper.toEntity(request)).thenReturn(mappedGrn);

            // Stock DOES NOT exist
            when(stockRepository.findByProductSkuAndLocationId("SKU-123", 10L)).thenReturn(Optional.empty());

            // Location lookup fails
            when(locationRepository.findById(10L)).thenReturn(Optional.empty());

            // When
            Throwable thrown = catchThrowable(() -> grnService.createGRN(request));

            // Then
            assertThat(thrown)
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Location not found");

            verify(stockRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw FinancialMismatchException when calculated subtotal does not match provided subtotal")
        void createGRN_WhenSubTotalMismatch_ThrowsException() {
            // Given
            // costPrice = 10, qty = 50 -> Calculated Subtotal = 500
            // But request subtotal is 400
            GRNCreateRequest invalidRequest = new GRNCreateRequest(
                    1L,
                    "INV-123",
                    BigDecimal.valueOf(400), // Invalid subtotal
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(400),
                    GRNStatus.POSTED,
                    List.of(buildBatchRequest())
            );

            when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

            // When
            Throwable thrown = catchThrowable(() -> grnService.createGRN(invalidRequest));

            // Then
            assertThat(thrown)
                    .isInstanceOf(FinancialMismatchException.class)
                    .hasMessageContaining("Subtotal mismatch! Calculated: 500");

            verify(grnRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw FinancialMismatchException when Grand Total does not equal Subtotal minus Discount")
        void createGRN_WhenGrandTotalMismatch_ThrowsException() {
            // Given
            // Subtotal = 500, Discount = 50 -> Expected Grand Total = 450
            // But request grand total is 500
            GRNCreateRequest invalidRequest = new GRNCreateRequest(
                    1L,
                    "INV-123",
                    BigDecimal.valueOf(500),
                    BigDecimal.valueOf(50),
                    BigDecimal.valueOf(500), // Invalid grand total
                    GRNStatus.POSTED,
                    List.of(buildBatchRequest())
            );

            when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

            // When
            Throwable thrown = catchThrowable(() -> grnService.createGRN(invalidRequest));

            // Then
            assertThat(thrown)
                    .isInstanceOf(FinancialMismatchException.class)
                    .hasMessageContaining("Grand Total mismatch! Expected: 450");

            verify(grnRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get All GRNs Tests")
    class GetAllGRNsTests {

        @Test
        @DisplayName("Should fetch GRNs, Batches, and Prices when full data exists")
        void getAllGRNs_WithFullData_Success() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // 1. Setup dummy data
            Batch batch = Batch.builder().id(50L).build();
            GRN grn = GRN.builder().id(1L).batches(List.of(batch)).build();

            Page<GRN> grnPage = new PageImpl<>(List.of(grn), pageable, 1);
            GRNResponse mockResponse = mock(GRNResponse.class);

            // 2. Mock Step 1: The initial paginated query
            when(grnRepository.findAll(ArgumentMatchers.<Specification<GRN>>any(), eq(pageable)))
                    .thenReturn(grnPage);

            // 3. Mock Step 2: The Level-1 Join (GRNs -> Batches)
            when(grnRepository.findAllByIdWithBatches(List.of(1L)))
                    .thenReturn(List.of(grn));

            // 4. Mock Step 3: The Level-2 Join (Batches -> Prices)
            // It returns a list, but the service ignores it (relies on Hibernate L1 cache),
            // so returning an empty list for the mock is fine.
            when(batchRepository.findAllWithPricesByIds(List.of(50L)))
                    .thenReturn(emptyList());

            // 5. Mock Step 4: The Mapper
            when(grnMapper.toResponse(grn)).thenReturn(mockResponse);

            // When
            Page<GRNResponse> result = grnService.getAllGRNs(
                    null, null, null, null, null, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).containsExactly(mockResponse);

            // Verify the sequence of calls happened correctly
            verify(grnRepository).findAllByIdWithBatches(List.of(1L));
            verify(batchRepository).findAllWithPricesByIds(List.of(50L));
            verify(grnMapper).toResponse(grn);
        }

        @Test
        @DisplayName("Should return an empty page immediately if no GRNs match the filters")
        void getAllGRNs_WhenNoResults_ReturnsEmptyPage() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // We use any(Specification.class) because the spec is created inside the method
            when(grnRepository.findAll(ArgumentMatchers.<Specification<GRN>>any(), eq(pageable)))
                    .thenReturn(Page.empty());

            // When
            Page<GRNResponse> result = grnService.getAllGRNs(
                    "INV-123", 1L, GRNStatus.POSTED, null, null, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isEmpty()).isTrue();

            // Verify we didn't execute unnecessary DB joins
            verify(grnRepository, never()).findAllByIdWithBatches(any());
            verifyNoInteractions(batchRepository);
            verifyNoInteractions(grnMapper);
        }

        @Test
        @DisplayName("Should skip fetching Prices if the fetched GRNs have zero batches")
        void getAllGRNs_WhenNoBatchesExist_SkipsPriceFetch() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            // GRN exists, but has an empty batch list
            GRN grn = GRN.builder().id(2L).batches(emptyList()).build();
            Page<GRN> grnPage = new PageImpl<>(List.of(grn), pageable, 1);
            GRNResponse mockResponse = mock(GRNResponse.class);

            when(grnRepository.findAll(ArgumentMatchers.<Specification<GRN>>any(), eq(pageable)))
                    .thenReturn(grnPage);

            when(grnRepository.findAllByIdWithBatches(List.of(2L)))
                    .thenReturn(List.of(grn));

            when(grnMapper.toResponse(grn)).thenReturn(mockResponse);

            // When
            Page<GRNResponse> result = grnService.getAllGRNs(
                    null, null, null, null, null, pageable);

            // Then
            assertThat(result.getContent()).containsExactly(mockResponse);

            // Verify that because batchIds was empty, we skipped the batchRepository call
            verifyNoInteractions(batchRepository);
        }
    }

    @Nested
    @DisplayName("Get GRN by ID Tests")
    class GetGRNByIdTests {

        @Test
        @DisplayName("Should fetch and return GRN response when ID exists")
        void getGRNById_WhenIdExists_ReturnsResponse() {
            // Given
            Long grnId = 1L;
            GRN mockGrn = GRN.builder().id(grnId).invoiceNum("INV-123").build();
            GRNResponse mockResponse = mock(GRNResponse.class);

            when(grnRepository.findByIdWithBatches(grnId)).thenReturn(Optional.of(mockGrn));
            when(grnMapper.toResponse(mockGrn)).thenReturn(mockResponse);

            // When
            GRNResponse result = grnService.getGRNById(grnId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(mockResponse);

            // Verify the repository and mapper were called correctly
            verify(grnRepository).findByIdWithBatches(grnId);
            verify(grnMapper).toResponse(mockGrn);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when ID does not exist")
        void getGRNById_WhenIdDoesNotExist_ThrowsException() {
            // Given
            Long nonExistentId = 99L;

            when(grnRepository.findByIdWithBatches(nonExistentId)).thenReturn(Optional.empty());

            // When
            Throwable thrown = catchThrowable(() -> grnService.getGRNById(nonExistentId));

            // Then
            assertThat(thrown)
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("GRN not found with id " + nonExistentId);

            // Verify that if it's not found, the mapper is never called
            verify(grnRepository).findByIdWithBatches(nonExistentId);
            org.mockito.Mockito.verifyNoInteractions(grnMapper);
        }
    }

    @Nested
    @DisplayName("Update GRN Tests")
    class UpdateGRNTests {

        @Test
        @DisplayName("Should successfully update GRN when Supplier is unchanged and Discount is null")
        void updateGRN_SameSupplierNullDiscount_Success() {
            // Given
            Long grnId = 1L;
            Supplier existingSupplier = Supplier.builder().id(1L).build();
            GRN existingGrn = GRN.builder()
                    .id(grnId)
                    .supplier(existingSupplier)
                    .status(GRNStatus.DRAFT)
                    .subTotalAmount(BigDecimal.valueOf(100.00))
                    .totalDiscount(BigDecimal.valueOf(10.00))
                    .grandTotalAmount(BigDecimal.valueOf(90.00))
                    .build();

            // Request keeps the same supplier (1L) and passes null for discount
            GRNUpdateRequest request = new GRNUpdateRequest(1L, "NEW-INV-123", null);
            GRNResponse mockResponse = mock(GRNResponse.class);

            when(grnRepository.findByIdWithBatches(grnId)).thenReturn(Optional.of(existingGrn));
            when(grnMapper.toResponse(existingGrn)).thenReturn(mockResponse);

            // When
            GRNResponse result = grnService.updateGRN(grnId, request);

            // Then
            assertThat(result).isEqualTo(mockResponse);

            // Verify Supplier was NOT fetched because it didn't change
            verify(supplierRepository, never()).findById(any());

            // Verify fields were updated correctly (null discount becomes ZERO)
            assertThat(existingGrn.getInvoiceNum()).isEqualTo("NEW-INV-123");
            assertThat(existingGrn.getTotalDiscount()).isEqualTo(BigDecimal.ZERO);
            assertThat(existingGrn.getGrandTotalAmount()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Should successfully update GRN when Supplier changes and Discount is applied")
        void updateGRN_NewSupplierAndValidDiscount_Success() {
            // Given
            Long grnId = 1L;
            Supplier oldSupplier = Supplier.builder().id(1L).build();
            Supplier newSupplier = Supplier.builder().id(2L).build();

            GRN existingGrn = GRN.builder()
                    .id(grnId)
                    .supplier(oldSupplier)
                    .status(GRNStatus.DRAFT)
                    .subTotalAmount(BigDecimal.valueOf(500.00))
                    .totalDiscount(BigDecimal.valueOf(10.00))
                    .grandTotalAmount(BigDecimal.valueOf(490.00))
                    .build();

            // Request has a NEW supplier (2L) and a valid discount (50)
            GRNUpdateRequest request = new GRNUpdateRequest(2L, "INV-999", BigDecimal.valueOf(50.00));
            GRNResponse mockResponse = mock(GRNResponse.class);

            when(grnRepository.findByIdWithBatches(grnId)).thenReturn(Optional.of(existingGrn));
            when(supplierRepository.findById(2L)).thenReturn(Optional.of(newSupplier));
            when(grnMapper.toResponse(existingGrn)).thenReturn(mockResponse);

            // When
            GRNResponse result = grnService.updateGRN(grnId, request);

            // Then
            assertThat(result).isEqualTo(mockResponse);

            // Verify new supplier was fetched and set
            verify(supplierRepository).findById(2L);
            assertThat(existingGrn.getSupplier()).isEqualTo(newSupplier);

            // Verify financials were recalculated (500 subtotal - 50 discount = 450 grand total)
            assertThat(existingGrn.getTotalDiscount()).isEqualByComparingTo("50.00");
            assertThat(existingGrn.getGrandTotalAmount()).isEqualByComparingTo("450.00");
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException if GRN does not exist")
        void updateGRN_WhenGrnNotFound_ThrowsException() {
            // Given
            Long nonExistentId = 99L;
            GRNUpdateRequest request = new GRNUpdateRequest(1L, "INV-123", BigDecimal.ZERO);

            when(grnRepository.findByIdWithBatches(nonExistentId)).thenReturn(Optional.empty());

            // When
            Throwable thrown = catchThrowable(() -> grnService.updateGRN(nonExistentId, request));

            // Then
            assertThat(thrown)
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("GRN not found with id " + nonExistentId);

            verify(grnRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw InvalidGRNStatusException if GRN is already cancelled")
        void updateGRN_WhenGrnIsCancelled_ThrowsException() {
            // Given
            Long grnId = 1L;
            GRN cancelledGrn = GRN.builder()
                    .id(grnId)
                    .status(GRNStatus.CANCELLED)
                    .build();
            GRNUpdateRequest request = new GRNUpdateRequest(1L, "INV-123", BigDecimal.ZERO);

            when(grnRepository.findByIdWithBatches(grnId)).thenReturn(Optional.of(cancelledGrn));

            // When
            Throwable thrown = catchThrowable(() -> grnService.updateGRN(grnId, request));

            // Then
            assertThat(thrown)
                    .isInstanceOf(InvalidGRNStatusException.class)
                    .hasMessageContaining("Cannot update a cancelled GRN");

            verify(grnRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException if requested new Supplier does not exist")
        void updateGRN_WhenNewSupplierNotFound_ThrowsException() {
            // Given
            Long grnId = 1L;
            Supplier oldSupplier = Supplier.builder().id(1L).build();
            GRN existingGrn = GRN.builder()
                    .id(grnId)
                    .supplier(oldSupplier)
                    .status(GRNStatus.DRAFT)
                    .build();

            // Requesting change to supplier 2L
            GRNUpdateRequest request = new GRNUpdateRequest(2L, "INV-123", BigDecimal.ZERO);

            when(grnRepository.findByIdWithBatches(grnId)).thenReturn(Optional.of(existingGrn));
            when(supplierRepository.findById(2L)).thenReturn(Optional.empty()); // Doesn't exist

            // When
            Throwable thrown = catchThrowable(() -> grnService.updateGRN(grnId, request));

            // Then
            assertThat(thrown)
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Supplier not found");

            verify(grnRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw FinancialMismatchException if discount exceeds subtotal")
        void updateGRN_WhenDiscountExceedsSubtotal_ThrowsException() {
            // Given
            Long grnId = 1L;
            Supplier supplier = Supplier.builder().id(1L).build();
            GRN existingGrn = GRN.builder()
                    .id(grnId)
                    .supplier(supplier)
                    .status(GRNStatus.DRAFT)
                    .subTotalAmount(BigDecimal.valueOf(100.00)) // Subtotal is 100
                    .totalDiscount(BigDecimal.valueOf(10.00))
                    .grandTotalAmount(BigDecimal.valueOf(90.00))
                    .build();

            // Request discount is 150 (invalid)
            GRNUpdateRequest request = new GRNUpdateRequest(1L, "INV-123", BigDecimal.valueOf(150.00));

            when(grnRepository.findByIdWithBatches(grnId)).thenReturn(Optional.of(existingGrn));

            // When
            Throwable thrown = catchThrowable(() -> grnService.updateGRN(grnId, request));

            // Then
            assertThat(thrown)
                    .isInstanceOf(FinancialMismatchException.class)
                    .hasMessageContaining("Discount cannot exceed subtotal");

            verify(grnRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Cancel GRN Tests")
    class CancelGRNTests {

        @Test
        @DisplayName("Should successfully cancel GRN, revert stock, and soft delete batches/prices")
        void cancelGRN_Success() {
            // Given
            Long grnId = 1L;
            Stock stock = Stock.builder().id(100L).productSku("SKU-123").build();

            ProductPrice price1 = ProductPrice.builder().id(10L).active(true).build();
            ProductPrice price2 = ProductPrice.builder().id(11L).active(true).build();

            Batch batch = Batch.builder()
                    .id(50L)
                    .stock(stock)
                    .quantity(BigDecimal.valueOf(25))
                    .status(BatchStatus.ACTIVE)
                    .prices(List.of(price1, price2))
                    .build();

            GRN existingGrn = GRN.builder()
                    .id(grnId)
                    .status(GRNStatus.POSTED)
                    .batches(List.of(batch))
                    .build();

            GRNResponse mockResponse = mock(GRNResponse.class);

            when(grnRepository.findByIdWithBatches(grnId)).thenReturn(Optional.of(existingGrn));

            // Mock stock decrement returning 1 (meaning 1 row was successfully updated)
            when(stockRepository.decrementQuantity(100L, BigDecimal.valueOf(25))).thenReturn(1);

            when(grnMapper.toResponse(existingGrn)).thenReturn(mockResponse);

            // When
            GRNResponse result = grnService.cancelGRN(grnId);

            // Then
            assertThat(result).isEqualTo(mockResponse);

            // Verify Stock Repository was called to decrement
            verify(stockRepository).decrementQuantity(100L, BigDecimal.valueOf(25));

            // Verify in-memory soft deletes occurred
            assertThat(existingGrn.getStatus()).isEqualTo(GRNStatus.CANCELLED);
            assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
            assertThat(price1.getActive()).isFalse();
            assertThat(price2.getActive()).isFalse();
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException if GRN does not exist")
        void cancelGRN_WhenGrnNotFound_ThrowsException() {
            // Given
            Long nonExistentId = 99L;
            when(grnRepository.findByIdWithBatches(nonExistentId)).thenReturn(Optional.empty());

            // When
            Throwable thrown = catchThrowable(() -> grnService.cancelGRN(nonExistentId));

            // Then
            assertThat(thrown)
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("GRN not found");

            // Verify no stock operations were attempted
            verifyNoInteractions(stockRepository);
        }

        @Test
        @DisplayName("Should throw InvalidGRNStatusException if GRN is already cancelled")
        void cancelGRN_WhenAlreadyCancelled_ThrowsException() {
            // Given
            Long grnId = 1L;
            GRN cancelledGrn = GRN.builder()
                    .id(grnId)
                    .status(GRNStatus.CANCELLED)
                    .build();

            when(grnRepository.findByIdWithBatches(grnId)).thenReturn(Optional.of(cancelledGrn));

            // When
            Throwable thrown = catchThrowable(() -> grnService.cancelGRN(grnId));

            // Then
            assertThat(thrown)
                    .isInstanceOf(InvalidGRNStatusException.class)
                    .hasMessageContaining("GRN already cancelled");

            // Verify no stock operations were attempted
            org.mockito.Mockito.verifyNoInteractions(stockRepository);
        }

        @Test
        @DisplayName("Should throw StockConflictException if decrementing stock fails (e.g., insufficient stock)")
        void cancelGRN_WhenStockConflictOccurs_ThrowsException() {
            // Given
            Long grnId = 1L;
            Stock stock1 = Stock.builder().id(100L).productSku("SKU-123").quantityOnHand(BigDecimal.valueOf(1000)).build();
            Stock stock2 = Stock.builder().id(101L).productSku("SKU-124").quantityOnHand(BigDecimal.valueOf(250)).build();

            Batch batch1 = Batch.builder()
                    .id(50L)
                    .stock(stock1)
                    .quantity(BigDecimal.valueOf(500))
                    .status(BatchStatus.ACTIVE)
                    .prices(List.of())
                    .build();

            Batch batch2 = Batch.builder()
                    .id(51L)
                    .stock(stock2)
                    .quantity(BigDecimal.valueOf(1000))
                    .status(BatchStatus.ACTIVE)
                    .prices(List.of())
                    .build();

            GRN existingGrn = GRN.builder()
                    .id(grnId)
                    .status(GRNStatus.POSTED)
                    .batches(List.of(batch1, batch2))
                    .build();

            when(grnRepository.findByIdWithBatches(grnId)).thenReturn(Optional.of(existingGrn));

            // Mock stock decrement returning 1 (meaning the DB query success)
            when(stockRepository.decrementQuantity(100L, BigDecimal.valueOf(500))).thenReturn(1);

            // Mock stock decrement returning 0 (meaning the DB query failed to update any rows due to constraints)
            when(stockRepository.decrementQuantity(101L, BigDecimal.valueOf(1000))).thenReturn(0);

            // When
            Throwable thrown = catchThrowable(() -> grnService.cancelGRN(grnId));

            // Then
            assertThat(thrown)
                    .isInstanceOf(StockConflictException.class)
                    // Assuming your StockConflictException takes the SKU as a constructor argument
                    .hasMessageContaining("SKU-124");

            // Verify the state of the entities was NOT changed since the exception aborted the process
            assertThat(existingGrn.getStatus()).isEqualTo(GRNStatus.POSTED);

            // batch1 was processed before the crash, so it became CANCELLED in RAM.
            // assertThat(batch1.getStatus()).isEqualTo(BatchStatus.ACTIVE);
            // We shouldn't assert it stayed ACTIVE, because Java doesn't rewind memory.
            // batch2 crashed BEFORE its status could be changed, so it is still ACTIVE.
            assertThat(batch2.getStatus()).isEqualTo(BatchStatus.ACTIVE);

            // Verify mapper was never called
            verifyNoInteractions(grnMapper);
        }

        @Test
        @DisplayName("Should skip stock decrement and processing for batches that are already CANCELLED")
        void cancelGRN_WhenBatchIsAlreadyCancelled_SkipsBatch() {
            // Given
            Long grnId = 1L;
            Stock stock1 = Stock.builder().id(100L).productSku("SKU-123").build();
            Stock stock2 = Stock.builder().id(101L).productSku("SKU-124").build();

            // Batch 1 is ALREADY CANCELLED
            Batch cancelledBatch = Batch.builder()
                    .id(50L)
                    .stock(stock1)
                    .quantity(BigDecimal.valueOf(50))
                    .status(BatchStatus.CANCELLED)
                    .prices(List.of())
                    .build();

            // Batch 2 is ACTIVE
            Batch activeBatch = Batch.builder()
                    .id(51L)
                    .stock(stock2)
                    .quantity(BigDecimal.valueOf(100))
                    .status(BatchStatus.ACTIVE)
                    .prices(List.of())
                    .build();

            GRN existingGrn = GRN.builder()
                    .id(grnId)
                    .status(GRNStatus.POSTED)
                    .batches(List.of(cancelledBatch, activeBatch))
                    .build();

            GRNResponse mockResponse = mock(GRNResponse.class);

            when(grnRepository.findByIdWithBatches(grnId)).thenReturn(Optional.of(existingGrn));

            // We only expect the repository to be called for the ACTIVE batch (stock2)
            when(stockRepository.decrementQuantity(101L, BigDecimal.valueOf(100))).thenReturn(1);

            when(grnMapper.toResponse(existingGrn)).thenReturn(mockResponse);

            // When
            GRNResponse result = grnService.cancelGRN(grnId);

            // Then
            assertThat(result).isEqualTo(mockResponse);

            // Verify the ACTIVE batch was processed normally
            verify(stockRepository).decrementQuantity(101L, BigDecimal.valueOf(100));
            assertThat(activeBatch.getStatus()).isEqualTo(BatchStatus.CANCELLED);

            // CRITICAL VERIFICATION: Prove the CANCELLED batch was completely skipped
            // We explicitly verify that decrementQuantity was NEVER called for stock1
            verify(stockRepository, never()).decrementQuantity(eq(100L), any());

            // Verify parent GRN was still canceled at the end
            assertThat(existingGrn.getStatus()).isEqualTo(GRNStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("Purge Cancelled GRN Tests")
    class PurgeCancelledGRNTests {

        @Test
        @DisplayName("Should successfully delete GRN when it exists and is CANCELLED")
        void purgeCancelledGRN_Success() {
            // Given
            Long grnId = 1L;
            GRN cancelledGrn = GRN.builder()
                    .id(grnId)
                    .status(GRNStatus.CANCELLED)
                    .build();

            when(grnRepository.findById(grnId)).thenReturn(Optional.of(cancelledGrn));

            // When
            grnService.purgeCancelledGRN(grnId);

            // Then
            // Verify that the repository's delete method was actually called with the correct object
            verify(grnRepository).delete(cancelledGrn);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException if GRN does not exist")
        void purgeCancelledGRN_WhenGrnNotFound_ThrowsException() {
            // Given
            Long nonExistentId = 99L;
            when(grnRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When
            Throwable thrown = catchThrowable(() -> grnService.purgeCancelledGRN(nonExistentId));

            // Then
            assertThat(thrown)
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("GRN not found");

            // Verify that we never accidentally called delete
            verify(grnRepository, never()).delete((GRN) any());
        }

        @Test
        @DisplayName("Should throw InvalidGRNStatusException if GRN is NOT CANCELLED (e.g., POSTED)")
        void purgeCancelledGRN_WhenGrnNotCancelled_ThrowsException() {
            // Given
            Long grnId = 1L;
            GRN postedGrn = GRN.builder()
                    .id(grnId)
                    .status(GRNStatus.POSTED) // Security violation!
                    .build();

            when(grnRepository.findById(grnId)).thenReturn(Optional.of(postedGrn));

            // When
            Throwable thrown = catchThrowable(() -> grnService.purgeCancelledGRN(grnId));

            // Then
            assertThat(thrown)
                    .isInstanceOf(InvalidGRNStatusException.class)
                    .hasMessageContaining("Only CANCELLED GRNs can be permanently deleted");

            // Verify that the deletion was strictly blocked
            verify(grnRepository, never()).delete((GRN) any());
        }

        @Test
        @DisplayName("Should throw InvalidGRNStatusException if GRN is DRAFT")
        void purgeCancelledGRN_WhenGrnIsDraft_ThrowsException() {
            // Given
            Long grnId = 1L;
            GRN draftGrn = GRN.builder()
                    .id(grnId)
                    .status(GRNStatus.DRAFT) // Security violation!
                    .build();

            when(grnRepository.findById(grnId)).thenReturn(Optional.of(draftGrn));

            // When
            Throwable thrown = catchThrowable(() -> grnService.purgeCancelledGRN(grnId));

            // Then
            assertThat(thrown)
                    .isInstanceOf(InvalidGRNStatusException.class);

            verify(grnRepository, never()).delete((GRN) any());
        }
    }


    // =====================================================================
    // Helper Methods
    // =====================================================================

    private GRNCreateRequest buildValidRequest() {
        // Cost: 10 * Qty: 50 = 500 Subtotal
        // 500 Subtotal - 50 Discount = 450 Grand Total
        return new GRNCreateRequest(
                1L,
                "INV-123",
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(450),
                GRNStatus.POSTED,
                List.of(buildBatchRequest())
        );
    }

    private BatchRequest buildBatchRequest() {
        ProductPriceRequest priceRequest = new ProductPriceRequest(
                null, PriceType.RETAIL, true, 1,
                BigDecimal.valueOf(15), BigDecimal.valueOf(10));

        return new BatchRequest(
                null,
                "SKU-123",
                10L,
                LocalDate.now().plusMonths(6),
                "BATCH-001",
                BigDecimal.valueOf(10), // Cost Price
                BigDecimal.valueOf(50), // Quantity
                BatchStatus.ACTIVE,
                List.of(priceRequest)
        );
    }
}