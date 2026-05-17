package com.zamzamsuper.inventory_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.zamzamsuper.inventory_service.exception.InvalidStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.zamzamsuper.inventory_service.dto.ProductPriceRequest;
import com.zamzamsuper.inventory_service.dto.batch.BatchCreateRequest;
import com.zamzamsuper.inventory_service.dto.batch.BatchResponse;
import com.zamzamsuper.inventory_service.dto.batch.BatchUpdateRequest;
import com.zamzamsuper.inventory_service.enums.BatchStatus;
import com.zamzamsuper.inventory_service.enums.GRNStatus;
import com.zamzamsuper.inventory_service.enums.PriceType;
import com.zamzamsuper.inventory_service.exception.FinancialMismatchException;
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
import com.zamzamsuper.inventory_service.repository.StockRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

    @Mock private BatchMapper batchMapper;
    @Mock private ProductPriceMapper priceMapper;
    @Mock private BatchRepository batchRepository;
    @Mock private GRNRepository grnRepository;
    @Mock private StockRepository stockRepository;

    @InjectMocks
    private BatchService batchService;

    private Stock stock;
    private GRN grn;

    @BeforeEach
    void setUp() {
        Location location = Location.builder().id(10L).name("Main Warehouse").build();
        stock = Stock.builder().id(100L).productSku("SKU-123").location(location).build();
        grn = GRN.builder()
                .id(1L)
                .status(GRNStatus.POSTED)
                .subTotalAmount(BigDecimal.valueOf(1000))
                .totalDiscount(BigDecimal.valueOf(100))
                .grandTotalAmount(BigDecimal.valueOf(900))
                .build();
    }

    @Nested
    @DisplayName("Create Batch Tests")
    class CreateBatchTests {

        @Test
        @DisplayName("Should create standalone batch successfully, increment stock, and adjust GRN financials")
        void createBatch_WithExistingStock_Success() {
            // Given
            BatchCreateRequest request = buildValidCreateRequest();
            Batch mappedBatch = new Batch();
            ProductPrice mappedPrice = new ProductPrice();
            BatchResponse expectedResponse = mock(BatchResponse.class);

            when(grnRepository.findById(1L)).thenReturn(Optional.of(grn));

            // Stock exists in DB
            when(stockRepository.findByProductSkuAndLocationId("SKU-123", 10L))
                    .thenReturn(Optional.of(stock));

            when(batchMapper.toEntity(request)).thenReturn(mappedBatch);
            when(priceMapper.toEntity(any(ProductPriceRequest.class))).thenReturn(mappedPrice);
            when(batchRepository.save(mappedBatch)).thenReturn(mappedBatch);
            when(batchMapper.toResponse(mappedBatch)).thenReturn(expectedResponse);

            // When
            BatchResponse response = batchService.createBatch(request);

            // Then
            assertThat(response).isEqualTo(expectedResponse);

            // Verify Stock was incremented and not created a new stock
            verify(stockRepository).incrementQuantity(100L, BigDecimal.valueOf(50));
            verify(stockRepository, never()).save(any(Stock.class));

            // Verify Financials were adjusted (Added 50 qty * 10 cost = 500 to GRN)
            assertThat(grn.getSubTotalAmount()).isEqualByComparingTo("1500");
            assertThat(grn.getGrandTotalAmount()).isEqualByComparingTo("1400"); // 1500 - 100 discount

            // Verify mappings
            assertThat(mappedBatch.getGrn()).isEqualTo(grn);
            assertThat(mappedBatch.getStock()).isEqualTo(stock);
            assertThat(mappedBatch.getPrices()).contains(mappedPrice);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when Parent GRN does not exist")
        void createBatch_WhenGrnNotFound_ThrowsException() {
            // Given
            BatchCreateRequest request = buildValidCreateRequest();
            when(grnRepository.findById(1L)).thenReturn(Optional.empty());

            // When
            Throwable thrown = catchThrowable(() -> batchService.createBatch(request));

            // Then
            assertThat(thrown)
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Parent GRN not found");

            verifyNoInteractions(stockRepository, batchRepository);
        }

        @Test
        @DisplayName("Should throw InvalidStatusException if parent GRN is CANCELLED")
        void createBatch_WhenGrnIsCancelled_ThrowsException() {
            // Given
            grn.setStatus(GRNStatus.CANCELLED);
            BatchCreateRequest request = buildValidCreateRequest();
            when(grnRepository.findById(1L)).thenReturn(Optional.of(grn));

            // When
            Throwable thrown = catchThrowable(() -> batchService.createBatch(request));

            // Then
            assertThat(thrown)
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessageContaining("Cannot add a new batch to a CANCELLED GRN");

            verifyNoInteractions(stockRepository, batchRepository);
        }
    }

    @Nested
    @DisplayName("Get All Batches Tests")
    class GetAllBatchesTests {

        @Test
        @DisplayName("Should fetch Batches and Prices when full data exists")
        void getAllBatches_WithFullData_Success() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Batch batch = Batch.builder().id(50L).build();
            Page<Batch> batchPage = new PageImpl<>(List.of(batch), pageable, 1);
            BatchResponse mockResponse = mock(BatchResponse.class);

            when(batchRepository.findAll(ArgumentMatchers.<Specification<Batch>>any(), eq(pageable)))
                    .thenReturn(batchPage);

            when(batchRepository.findAllByIdWithPrices(List.of(50L)))
                    .thenReturn(List.of(batch));

            when(batchMapper.toResponse(batch)).thenReturn(mockResponse);

            // When
            Page<BatchResponse> result = batchService.getAllBatches(
                    null, null, null, null, null, pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).containsExactly(mockResponse);

            verify(batchRepository).findAllByIdWithPrices(List.of(50L));
        }

        @Test
        @DisplayName("Should return an empty page immediately if no Batches match the filters")
        void getAllBatches_WhenNoResults_ReturnsEmptyPage() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);

            when(batchRepository.findAll(ArgumentMatchers.<Specification<Batch>>any(), eq(pageable)))
                    .thenReturn(Page.empty());

            // When
            Page<BatchResponse> result = batchService.getAllBatches(
                    "SKU-123", null, null, null, null, pageable);

            // Then
            assertThat(result.isEmpty()).isTrue();

            verify(batchRepository, never()).findAllByIdWithPrices(any());
            verifyNoInteractions(batchMapper);
        }
    }

    @Nested
    @DisplayName("Get Batch by ID Tests")
    class GetBatchByIdTests {

        @Test
        @DisplayName("Should fetch and return Batch response when ID exists")
        void getBatchById_WhenIdExists_ReturnsResponse() {
            // Given
            Long batchId = 1L;
            Batch mockBatch = Batch.builder().id(batchId).batchNumber("BATCH-123").build();
            BatchResponse mockResponse = mock(BatchResponse.class);

            when(batchRepository.findByIdWithPrices(batchId)).thenReturn(Optional.of(mockBatch));
            when(batchMapper.toResponse(mockBatch)).thenReturn(mockResponse);

            // When
            BatchResponse result = batchService.getBatchById(batchId);

            // Then
            assertThat(result).isEqualTo(mockResponse);
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when ID does not exist")
        void getBatchById_WhenIdDoesNotExist_ThrowsException() {
            // Given
            Long nonExistentId = 99L;
            when(batchRepository.findByIdWithPrices(nonExistentId)).thenReturn(Optional.empty());

            // When
            Throwable thrown = catchThrowable(() -> batchService.getBatchById(nonExistentId));

            // Then
            assertThat(thrown)
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Batch not found with id " + nonExistentId);
        }
    }

    @Nested
    @DisplayName("Update Batch Tests")
    class UpdateBatchTests {

        @Test
        @DisplayName("Should update non-critical fields without hitting stock or financials")
        void updateBatch_NonCriticalFields_Success() {
            // Given
            Long batchId = 1L;
            Batch existingBatch = Batch.builder()
                    .id(batchId)
                    .quantity(BigDecimal.valueOf(50))
                    .costPrice(BigDecimal.valueOf(10))
                    .status(BatchStatus.ACTIVE)
                    .build();

            // Request updates Expiry and Batch Number, but leaves cost and quantity null
            LocalDate newExpiry = LocalDate.now().plusMonths(1);
            BatchUpdateRequest request = new BatchUpdateRequest(newExpiry, null, null);
            BatchResponse mockResponse = mock(BatchResponse.class);

            when(batchRepository.findByIdWithPrices(batchId)).thenReturn(Optional.of(existingBatch));
            when(batchMapper.toResponse(existingBatch)).thenReturn(mockResponse);

            // When
            BatchResponse result = batchService.updateBatch(batchId, request);

            // Then
            assertThat(result).isEqualTo(mockResponse);
            assertThat(existingBatch.getExpiryDate()).isEqualTo(newExpiry);

            // Verify Stock and GRN Financials were NOT touched
            verifyNoInteractions(stockRepository, grnRepository);
        }

        @Test
        @DisplayName("Should safely increase stock and GRN financials when quantity increases")
        void updateBatch_IncreaseQuantity_Success() {
            // Given
            Long batchId = 1L;
            Batch existingBatch = Batch.builder()
                    .id(batchId)
                    .quantity(BigDecimal.valueOf(10)) // Old qty = 10
                    .costPrice(BigDecimal.valueOf(10)) // Cost = 10 (Total 100)
                    .status(BatchStatus.ACTIVE)
                    .stock(stock)
                    .grn(grn) // GRN subtotal = 1000, discount = 100, grand = 900
                    .build();

            // Increase quantity to 20
            BatchUpdateRequest request = new BatchUpdateRequest(null, null, BigDecimal.valueOf(20));
            BatchResponse mockResponse = mock(BatchResponse.class);

            when(batchRepository.findByIdWithPrices(batchId)).thenReturn(Optional.of(existingBatch));
            when(batchMapper.toResponse(existingBatch)).thenReturn(mockResponse);

            // When
            batchService.updateBatch(batchId, request);

            // Then
            // Verify Stock incremented by the difference (10)
            verify(stockRepository).incrementQuantity(stock.getId(), BigDecimal.valueOf(10));

            // Verify Financials adjusted by diff: +10 qty * 10 cost = +100
            assertThat(grn.getSubTotalAmount()).isEqualByComparingTo("1100");
            assertThat(grn.getGrandTotalAmount()).isEqualByComparingTo("1000"); // 1100 - 100
            assertThat(existingBatch.getQuantity()).isEqualByComparingTo("20");
        }

        @Test
        @DisplayName("Should safely decrease stock and GRN financials when quantity decreases")
        void updateBatch_DecreaseQuantity_Success() {
            // Given
            Long batchId = 1L;
            Batch existingBatch = Batch.builder()
                    .id(batchId)
                    .quantity(BigDecimal.valueOf(50)) // Old qty = 50
                    .costPrice(BigDecimal.valueOf(10)) // Cost = 10 (Total 500)
                    .status(BatchStatus.ACTIVE)
                    .stock(stock)
                    .grn(grn) // GRN subtotal = 1000, discount = 100, grand = 900
                    .build();

            // Decrease quantity to 40
            BatchUpdateRequest request = new BatchUpdateRequest(null, null, BigDecimal.valueOf(40));
            BatchResponse mockResponse = mock(BatchResponse.class);

            when(batchRepository.findByIdWithPrices(batchId)).thenReturn(Optional.of(existingBatch));
            // Mock stock decrement returning 1 (meaning DB success)
            when(stockRepository.decrementQuantity(stock.getId(), BigDecimal.valueOf(10))).thenReturn(1);
            when(batchMapper.toResponse(existingBatch)).thenReturn(mockResponse);

            // When
            batchService.updateBatch(batchId, request);

            // Then
            // Verify Stock decremented by absolute difference (10)
            verify(stockRepository).decrementQuantity(stock.getId(), BigDecimal.valueOf(10));

            // Verify Financials adjusted by diff: -10 qty * 10 cost = -100
            assertThat(grn.getSubTotalAmount()).isEqualByComparingTo("900");
            assertThat(grn.getGrandTotalAmount()).isEqualByComparingTo("800"); // 900 - 100
            assertThat(existingBatch.getQuantity()).isEqualByComparingTo("40");
        }

        @Test
        @DisplayName("Should throw StockConflictException if stock decrement fails during update")
        void updateBatch_DecreaseQuantity_StockConflict_ThrowsException() {
            // Given
            Long batchId = 1L;
            Batch existingBatch = Batch.builder()
                    .id(batchId)
                    .quantity(BigDecimal.valueOf(50))
                    .costPrice(BigDecimal.valueOf(10))
                    .status(BatchStatus.ACTIVE)
                    .stock(stock)
                    .grn(grn)
                    .build();

            BatchUpdateRequest request = new BatchUpdateRequest(null, null, BigDecimal.valueOf(10));

            when(batchRepository.findByIdWithPrices(batchId)).thenReturn(Optional.of(existingBatch));

            // Mock DB returning 0 updated rows
            when(stockRepository.decrementQuantity(stock.getId(), BigDecimal.valueOf(40))).thenReturn(0);

            // When
            Throwable thrown = catchThrowable(() -> batchService.updateBatch(batchId, request));

            // Then
            assertThat(thrown)
                    .isInstanceOf(StockConflictException.class)
                    .hasMessageContaining("SKU-123");
        }

        @Test
        @DisplayName("Should throw FinancialMismatchException if decreasing quantity pushes GRN Grand Total below zero")
        void updateBatch_DecreaseQuantity_NegativeGrandTotal_ThrowsException() {
            // Given
            Long batchId = 1L;
            // A situation where the GRN has a massive flat discount
            GRN heavilyDiscountedGrn = GRN.builder()
                    .id(1L)
                    .subTotalAmount(BigDecimal.valueOf(100))
                    .totalDiscount(BigDecimal.valueOf(100))
                    .grandTotalAmount(BigDecimal.valueOf(0))
                    .build();

            Batch existingBatch = Batch.builder()
                    .id(batchId)
                    .quantity(BigDecimal.valueOf(10))
                    .costPrice(BigDecimal.valueOf(10)) // Total 100
                    .status(BatchStatus.ACTIVE)
                    .stock(stock)
                    .grn(heavilyDiscountedGrn)
                    .build();

            // Reducing quantity to 5 means we subtract 50 from GRN subtotal.
            // New subtotal: 50. Discount: 100. New Grand Total: -50. (Exception should trigger!)
            BatchUpdateRequest request = new BatchUpdateRequest(null, null, BigDecimal.valueOf(5));

            when(batchRepository.findByIdWithPrices(batchId)).thenReturn(Optional.of(existingBatch));
            when(stockRepository.decrementQuantity(stock.getId(), BigDecimal.valueOf(5))).thenReturn(1);

            // When
            Throwable thrown = catchThrowable(() -> batchService.updateBatch(batchId, request));

            // Then
            assertThat(thrown)
                    .isInstanceOf(FinancialMismatchException.class)
                    .hasMessageContaining("causes the GRN Grand Total to drop below zero");
        }
    }

    @Nested
    @DisplayName("Cancel Batch Tests")
    class CancelBatchTests {

        @Test
        @DisplayName("Should successfully revert stock, financials, and soft-delete batch/prices")
        void cancelBatch_Success() {
            // Given
            Long batchId = 1L;
            ProductPrice price = ProductPrice.builder().id(10L).active(true).build();

            Batch existingBatch = Batch.builder()
                    .id(batchId)
                    .quantity(BigDecimal.valueOf(50))
                    .costPrice(BigDecimal.valueOf(10)) // Value to deduct = 500
                    .status(BatchStatus.ACTIVE)
                    .stock(stock)
                    .grn(grn) // Subtotal: 1000, Discount: 100, Grand: 900
                    .prices(List.of(price))
                    .build();

            BatchResponse mockResponse = mock(BatchResponse.class);

            when(batchRepository.findByIdWithPrices(batchId)).thenReturn(Optional.of(existingBatch));
            when(stockRepository.decrementQuantity(stock.getId(), BigDecimal.valueOf(50))).thenReturn(1);
            when(batchMapper.toResponse(existingBatch)).thenReturn(mockResponse);

            // When
            BatchResponse result = batchService.cancelBatch(batchId);

            // Then
            assertThat(result).isEqualTo(mockResponse);

            // Verify Stock
            verify(stockRepository).decrementQuantity(stock.getId(), BigDecimal.valueOf(50));

            // Verify Financials (1000 - 500 = 500 Subtotal. 500 - 100 discount = 400 Grand)
            assertThat(grn.getSubTotalAmount()).isEqualByComparingTo("500");
            assertThat(grn.getGrandTotalAmount()).isEqualByComparingTo("400");

            // Verify statuses
            assertThat(existingBatch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
            assertThat(price.getActive()).isFalse();
        }

        @Test
        @DisplayName("Should throw InvalidStatusException if batch is already CANCELLED")
        void cancelBatch_WhenAlreadyCancelled_ThrowsException() {
            // Given
            Long batchId = 1L;
            Batch cancelledBatch = Batch.builder().id(batchId).status(BatchStatus.CANCELLED).build();

            when(batchRepository.findByIdWithPrices(batchId)).thenReturn(Optional.of(cancelledBatch));

            // When
            Throwable thrown = catchThrowable(() -> batchService.cancelBatch(batchId));

            // Then
            assertThat(thrown)
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessageContaining("Batch already cancelled");

            verifyNoInteractions(stockRepository);
        }
    }

    @Nested
    @DisplayName("Purge Cancelled Batch Tests")
    class PurgeCancelledBatchTests {

        @Test
        @DisplayName("Should successfully delete batch when it is CANCELLED")
        void purgeCancelledBatch_Success() {
            // Given
            Long batchId = 1L;
            Batch cancelledBatch = Batch.builder().id(batchId).status(BatchStatus.CANCELLED).build();

            when(batchRepository.findById(batchId)).thenReturn(Optional.of(cancelledBatch));

            // When
            batchService.purgeCancelledBatch(batchId);

            // Then
            verify(batchRepository).delete(cancelledBatch);
        }

        @Test
        @DisplayName("Should throw InvalidStatusException if batch is NOT CANCELLED (e.g., ACTIVE)")
        void purgeCancelledBatch_WhenBatchNotCancelled_ThrowsException() {
            // Given
            Long batchId = 1L;
            Batch activeBatch = Batch.builder().id(batchId).status(BatchStatus.ACTIVE).build();

            when(batchRepository.findById(batchId)).thenReturn(Optional.of(activeBatch));

            // When
            Throwable thrown = catchThrowable(() -> batchService.purgeCancelledBatch(batchId));

            // Then
            assertThat(thrown)
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessageContaining("Only CANCELLED Batches can be permanently deleted");

            verify(batchRepository, never()).delete(any(Batch.class));
        }
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private BatchCreateRequest buildValidCreateRequest() {
        ProductPriceRequest priceRequest = new ProductPriceRequest(
                null, PriceType.RETAIL, true, 1,
                BigDecimal.valueOf(15), BigDecimal.valueOf(10));

        return new BatchCreateRequest(
                1L,
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