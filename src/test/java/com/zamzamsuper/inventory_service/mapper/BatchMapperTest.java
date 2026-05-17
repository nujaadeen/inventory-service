package com.zamzamsuper.inventory_service.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zamzamsuper.inventory_service.dto.ProductPriceRequest;
import com.zamzamsuper.inventory_service.dto.ProductPriceResponse;
import com.zamzamsuper.inventory_service.dto.batch.BatchCreateRequest;
import com.zamzamsuper.inventory_service.dto.batch.BatchResponse;
import com.zamzamsuper.inventory_service.enums.BatchStatus;
import com.zamzamsuper.inventory_service.enums.PriceType;
import com.zamzamsuper.inventory_service.model.Batch;
import com.zamzamsuper.inventory_service.model.GRN;
import com.zamzamsuper.inventory_service.model.ProductPrice;
import com.zamzamsuper.inventory_service.model.Stock;

@ExtendWith(MockitoExtension.class)
class BatchMapperTest {

    @Mock private ProductPriceMapper priceMapper; // Mocked because BatchMapper uses it

    // MapStruct generates the implementation class with an "Impl" suffix.
    // @InjectMocks tells Mockito to inject the mocked ProductPriceMapper into it.
    @InjectMocks private BatchMapperImpl batchMapper;

    @Nested
    @DisplayName("Tests for toResponse(Batch)")
    class ToResponseTests {

        @Test
        @DisplayName("Should correctly map Batch, flattened Stock/GRN IDs, and use PriceMapper for prices")
        void toResponse_Success() {
            // Given
            Stock stock = Stock.builder()
                    .id(100L)
                    .productSku("SKU-123")
                    .build();

            GRN grn = GRN.builder()
                    .id(200L)
                    .invoiceNum("INV-123")
                    .build();

            // Create a dummy ProductPrice to put inside the Batch
            ProductPrice price = ProductPrice.builder()
                    .id(50L)
                    .priceType(PriceType.RETAIL)
                    .price(BigDecimal.valueOf(15.00))
                    .build();

            Batch batch = Batch.builder()
                    .id(1L)
                    .batchNumber("BATCH-TEST")
                    .costPrice(BigDecimal.valueOf(10.00))
                    .quantity(BigDecimal.valueOf(50))
                    .status(BatchStatus.ACTIVE)
                    .stock(stock)
                    .grn(grn)
                    .prices(List.of(price)) // Add the price here
                    .build();

            // Create a mock or dummy ProductPriceResponse
            ProductPriceResponse mockPriceResponse = mock(ProductPriceResponse.class);

            // Tell Mockito: When PriceMapper is asked to map this specific 'price', return 'mockPriceResponse'
            when(priceMapper.toResponse(price)).thenReturn(mockPriceResponse);

            // When
            BatchResponse response = batchMapper.toResponse(batch);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.batchNumber()).isEqualTo("BATCH-TEST");
            assertThat(response.costPrice()).isEqualByComparingTo("10.00");
            assertThat(response.quantity()).isEqualByComparingTo("50");

            // Verify custom flattened mappings
            assertThat(response.stockId()).isEqualTo(100L);
            assertThat(response.grnId()).isEqualTo(200L);

            // Verify that the prices list was mapped using the injected PriceMapper
            assertThat(response.prices()).isNotNull();
            assertThat(response.prices()).hasSize(1);
            assertThat(response.prices().getFirst()).isEqualTo(mockPriceResponse);

            // Verify that the priceMapper was actually called exactly once
            verify(priceMapper).toResponse(price);
        }

        @Test
        @DisplayName("Should gracefully handle null Stock, GRN, and Prices without throwing Exceptions")
        void toResponse_WhenStockGrnAndPricesAreNull_ShouldMapFieldsToNull() {
            // Given
            Batch batch = Batch.builder()
                    .id(2L)
                    .batchNumber("BATCH-NULL-TEST")
                    .stock(null)  // Deliberately null
                    .grn(null)    // Deliberately null
                    .prices(null) // Deliberately null
                    .build();

            // When
            BatchResponse response = batchMapper.toResponse(batch);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(2L);
            assertThat(response.batchNumber()).isEqualTo("BATCH-NULL-TEST");

            // Custom flattened mappings should be null safely
            assertThat(response.stockId()).isNull();
            assertThat(response.grnId()).isNull();

            // Prices should be safely handled
            assertThat(response.prices()).isNullOrEmpty();

            // Verify PriceMapper was never called since there were no prices
            verifyNoInteractions(priceMapper);
        }

        @Test
        @DisplayName("Should return null if source Batch is null")
        void toResponse_WhenSourceIsNull_ShouldReturnNull() {
            BatchResponse response = batchMapper.toResponse(null);
            assertThat(response).isNull();
        }
    }

    @Nested
    @DisplayName("Tests for toEntity(BatchCreateRequest)")
    class ToEntityTests {

        @Test
        @DisplayName("Should correctly map request fields and ignore specified target fields")
        void toEntity_Success() {
            // Given
            ProductPriceRequest priceRequest =
                    new ProductPriceRequest(
                            null,
                            PriceType.RETAIL,
                            true,
                            1,
                            BigDecimal.valueOf(75),
                            BigDecimal.valueOf(70));

            BatchCreateRequest request =
                    new BatchCreateRequest(
                            200L, // grnId
                            "SKU-001",
                            1L, // locationId
                            LocalDate.now().plusMonths(6),
                            "BATCH-001",
                            BigDecimal.valueOf(50),
                            BigDecimal.valueOf(100),
                            BatchStatus.ACTIVE,
                            List.of(priceRequest));

            // When
            Batch entity = batchMapper.toEntity(request);

            // Then
            assertThat(entity).isNotNull();
            assertThat(entity.getBatchNumber()).isEqualTo("BATCH-001");
            assertThat(entity.getCostPrice()).isEqualByComparingTo("50");
            assertThat(entity.getQuantity()).isEqualByComparingTo("100");
            assertThat(entity.getExpiryDate()).isEqualTo(request.expiryDate());

            // Verify default status (even though it's provided in the request, it should map it)
            assertThat(entity.getStatus()).isEqualTo(BatchStatus.ACTIVE);

            // Verify explicitly ignored fields (@Mapping(target = "...", ignore = true))
            assertThat(entity.getId()).isNull();
            assertThat(entity.getGrn()).isNull(); // grn is ignored, set in service layer
            assertThat(entity.getStock()).isNull(); // stock is ignored, set in service layer
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();

            // Verify that despite having ProductPriceRequests, the prices field is ignored
            assertThat(entity.getPrices()).isNullOrEmpty();
        }

        @Test
        @DisplayName("Should set default status to ACTIVE when status is null in request")
        void toEntity_WhenStatusIsNull_ShouldSetDefaultActive() {
            // Given a request with a null status
            BatchCreateRequest request =
                    new BatchCreateRequest(
                            200L, "SKU-001", 1L, LocalDate.now(), "BATCH-002",
                            BigDecimal.TEN, BigDecimal.TEN, null, List.of()); // status is null

            // When
            Batch entity = batchMapper.toEntity(request);

            // Then
            assertThat(entity.getStatus()).isEqualTo(BatchStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return null if source BatchCreateRequest is null")
        void toEntity_WhenSourceIsNull_ShouldReturnNull() {
            Batch entity = batchMapper.toEntity(null);
            assertThat(entity).isNull();
        }
    }
}