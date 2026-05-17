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

import com.zamzamsuper.inventory_service.dto.batch.BatchRequest;
import com.zamzamsuper.inventory_service.dto.batch.BatchResponse;
import com.zamzamsuper.inventory_service.dto.ProductPriceRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNCreateRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNResponse;
import com.zamzamsuper.inventory_service.enums.BatchStatus;
import com.zamzamsuper.inventory_service.enums.GRNStatus;
import com.zamzamsuper.inventory_service.enums.PriceType;
import com.zamzamsuper.inventory_service.model.Batch;
import com.zamzamsuper.inventory_service.model.GRN;
import com.zamzamsuper.inventory_service.model.Supplier;

@ExtendWith(MockitoExtension.class)
class GRNMapperTest {

    @Mock private BatchMapper batchMapper; // Mocked because GRNMapper uses it

    // MapStruct generates the implementation class with an "Impl" suffix.
    // @InjectMocks tells Mockito to inject the mocked BatchMapper into it.
    @InjectMocks private GRNMapperImpl grnMapper;

    @Nested
    @DisplayName("Tests for toResponse(GRN)")
    class ToResponseTests {

        @Test
        @DisplayName("Should correctly map GRN, flattened Supplier, and use BatchMapper for batches")
        void toResponse_Success() {
            // Given
            Supplier supplier = Supplier.builder()
                    .id(100L)
                    .name("Super Supplies Co.")
                    .build();

            // Create a dummy Batch to put inside the GRN
            Batch batch = Batch.builder()
                    .id(50L)
                    .batchNumber("BATCH-TEST")
                    .build();

            GRN grn = GRN.builder()
                    .id(1L)
                    .invoiceNum("INV-123")
                    .subTotalAmount(BigDecimal.valueOf(500.00))
                    .status(GRNStatus.POSTED)
                    .supplier(supplier)
                    .batches(List.of(batch)) // Add the batch here
                    .build();

            // Create a mock or dummy BatchResponse
            // Since records are final, Mockito can mock them using the inline mock maker
            BatchResponse mockBatchResponse = mock(BatchResponse.class);

            // Tell Mockito: When BatchMapper is asked to map this specific 'batch', return 'mockBatchResponse'
            // (Assuming the method in BatchMapper is named toResponse)
            when(batchMapper.toResponse(batch)).thenReturn(mockBatchResponse);

            // When
            GRNResponse response = grnMapper.toResponse(grn);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.invoiceNum()).isEqualTo("INV-123");
            assertThat(response.subTotalAmount()).isEqualByComparingTo("500.00");

            // Verify custom mappings
            assertThat(response.supplierId()).isEqualTo(100L);
            assertThat(response.supplierName()).isEqualTo("Super Supplies Co.");

            // Verify that the batches list was mapped using the injected BatchMapper
            assertThat(response.batches()).isNotNull();
            assertThat(response.batches()).hasSize(1);
            assertThat(response.batches().getFirst()).isEqualTo(mockBatchResponse);

            // Verify that the batchMapper was actually called exactly once
            verify(batchMapper).toResponse(batch);
        }

        @Test
        @DisplayName("Should gracefully handle null Supplier and null Batches without throwing Exceptions")
        void toResponse_WhenSupplierAndBatchesAreNull_ShouldMapFieldsToNull() {
            // Given
            GRN grn = GRN.builder()
                    .id(2L)
                    .invoiceNum("INV-456")
                    .supplier(null) // Deliberately null
                    .batches(null)  // Deliberately null
                    .build();

            // When
            GRNResponse response = grnMapper.toResponse(grn);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(2L);
            assertThat(response.invoiceNum()).isEqualTo("INV-456");

            // Custom mappings should be null safely
            assertThat(response.supplierId()).isNull();
            assertThat(response.supplierName()).isNull();
            
            // Batches should be safely handled (usually null or empty list depending on MapStruct config)
            assertThat(response.batches()).isNullOrEmpty();
            
            // Verify BatchMapper was never called since there were no batches
            verifyNoInteractions(batchMapper);
        }

        @Test
        @DisplayName("Should return null if source GRN is null")
        void toResponse_WhenSourceIsNull_ShouldReturnNull() {
            GRNResponse response = grnMapper.toResponse(null);
            assertThat(response).isNull();
        }
    }

    @Nested
    @DisplayName("Tests for toEntity(GRNCreateRequest)")
    class ToEntityTests {

        @Test
        @DisplayName("Should correctly map request fields and ignore specified target fields")
        void toEntity_Success() {
            // Given
            // Assuming your request has basic fields like invoiceNum
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
                    BigDecimal.valueOf(10),
                    BatchStatus.ACTIVE,
                    List.of(priceRequest));

            GRNCreateRequest request = new GRNCreateRequest(
                1L,
                "INV-001",
                BigDecimal.valueOf(500),
                BigDecimal.ZERO,
                BigDecimal.valueOf(500),
                GRNStatus.POSTED,
                List.of(batchRequest));

            // When
            GRN entity = grnMapper.toEntity(request);

            // Then
            assertThat(entity).isNotNull();
            assertThat(entity.getInvoiceNum()).isEqualTo("INV-001");
            assertThat(entity.getSubTotalAmount()).isEqualByComparingTo("500");
            assertThat(entity.getTotalDiscount()).isEqualByComparingTo("0");
            assertThat(entity.getGrandTotalAmount()).isEqualByComparingTo("500");

            // Verify default status
            assertThat(entity.getStatus()).isEqualTo(GRNStatus.POSTED);

            // Verify explicitly ignored fields (@Mapping(target = "...", ignore = true))
            assertThat(entity.getId()).isNull();
            assertThat(entity.getSupplier()).isNull(); // supplier is ignored, set in service layer
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();

            // Verify that despite having BatchRequests, the batches field is ignored
            assertThat(entity.getBatches()).isNullOrEmpty();
        }

        @Test
        @DisplayName("Should return null if source GRNCreateRequest is null")
        void toEntity_WhenSourceIsNull_ShouldReturnNull() {
            GRN entity = grnMapper.toEntity(null);
            assertThat(entity).isNull();
        }
    }
}