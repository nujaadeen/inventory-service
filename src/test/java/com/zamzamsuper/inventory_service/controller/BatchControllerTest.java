package com.zamzamsuper.inventory_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zamzamsuper.inventory_service.dto.batch.BatchCreateRequest;
import com.zamzamsuper.inventory_service.dto.batch.BatchResponse;
import com.zamzamsuper.inventory_service.dto.batch.BatchUpdateRequest;
import com.zamzamsuper.inventory_service.exception.GlobalExceptionHandler;
import com.zamzamsuper.inventory_service.service.BatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BatchController.class)
@Import(GlobalExceptionHandler.class)
class BatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BatchService batchService;

    @Nested
    @DisplayName("Create Standalone Batch Tests")
    class CreateBatchTests {

        @Test
        @DisplayName("POST /batches - 201 on valid payload")
        void createBatch_ValidPayload_Returns201() throws Exception {
            when(batchService.createBatch(any(BatchCreateRequest.class))).thenReturn(mock(BatchResponse.class));

            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "costPrice": 10.00,
                    "quantity": 50,
                    "prices": [
                        {
                            "priceType": "RETAIL",
                            "price": 15.00
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Batch created successfully"))
                    .andExpect(jsonPath("$.data").exists());
        }

        // =====================================================================
        // Creation Params Validations:
        // =====================================================================

        @Test
        @DisplayName("POST /batches - 400 when grnId is missing (StandaloneBatchCreation group triggers)")
        void createBatch_MissingGrnId_Returns400() throws Exception {
            String json = """
                {
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "costPrice": 10.00,
                    "quantity": 50,
                    "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.grnId").value("GRN ID is required when creating a standalone batch"));
        }

        @Test
        @DisplayName("POST /batches - 400 when productSku is blank")
        void createBatch_BlankProductSku_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "   ",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "costPrice": 10.00,
                    "quantity": 50,
                    "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.productSku").value("Product SKU is required"));
        }

        @Test
        @DisplayName("POST /batches - 400 when locationId is missing")
        void createBatch_MissingLocationId_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "batchNumber": "BATCH-001",
                    "costPrice": 10.00,
                    "quantity": 50,
                    "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.locationId").value("Location ID is required"));
        }

        @Test
        @DisplayName("POST /batches - 400 when batchNumber is missing")
        void createBatch_MissingBatchNumber_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "costPrice": 10.00,
                    "quantity": 50,
                    "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.batchNumber").value("Batch number is required"));
        }

        @Test
        @DisplayName("POST /batches - 400 when costPrice is missing")
        void createBatch_MissingCostPrice_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "quantity": 50,
                    "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.costPrice").value("Cost price is required"));
        }

        @Test
        @DisplayName("POST /batches - 400 when costPrice is non-positive")
        void createBatch_NonPositiveCostPrice_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "costPrice": -5.00,
                    "quantity": 50,
                    "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.costPrice").value("Cost price must be positive"));
        }

        @Test
        @DisplayName("POST /batches - 400 when quantity is missing")
        void createBatch_MissingQuantity_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "costPrice": 10.00,
                    "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.quantity").value("Quantity is required"));
        }

        @Test
        @DisplayName("POST /batches - 400 when quantity is less than 1")
        void createBatch_QuantityLessThanOne_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "costPrice": 10.00,
                    "quantity": 0,
                    "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.quantity").value("Quantity must be at least 1"));
        }

        @Test
        @DisplayName("POST /batches - 400 when prices list is empty")
        void createBatch_EmptyPricesList_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "costPrice": 10.00,
                    "quantity": 50,
                    "prices": []
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.prices").value("At least one price type must be defined"));
        }

        // ── Nested ProductPrice Validation ───────────────────────────────────────────

        @Test
        @DisplayName("POST /batches - 400 when price entry is missing priceType")
        void createBatch_PriceMissingPriceType_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "costPrice": 10.00,
                    "quantity": 50,
                    "prices": [
                        {
                            "price": 10.00,
                            "minQuantity": 5
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['prices[0].priceType']").value("Price type is required"));
        }

        @Test
        @DisplayName("POST /batches - 400 when minQuantity is negative")
        void createBatch_PriceNegativeMinQuantity_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "costPrice": 10.00,
                    "quantity": 50,
                    "prices": [
                        {
                            "priceType": "RETAIL",
                            "price": 10.00,
                            "minQuantity": -5
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['prices[0].minQuantity']")
                            .value("Min quantity cannot be negative"));
        }

        @Test
        @DisplayName("POST /batches - 400 when price entry is missing price field")
        void createBatch_PriceMissingPriceField_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "costPrice": 10.00,
                    "quantity": 50,
                    "prices": [
                        {
                            "priceType": "RETAIL",
                            "minQuantity": 5
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['prices[0].price']").value("Price is required"));
        }

        @Test
        @DisplayName("POST /batches - 400 when price is zero (must be positive)")
        void createBatch_PriceIsZero_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "costPrice": 10.00,
                    "quantity": 50,
                    "prices": [
                        {
                            "priceType": "RETAIL",
                            "price": 0.00,
                            "minQuantity": 5
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['prices[0].price']")
                            .value("Price must be greater than 0"));
        }

        @Test
        @DisplayName("POST /batches - 400 when minPrice is negative")
        void createBatch_NegativeMinPrice_Returns400() throws Exception {
            String json = """
                {
                    "grnId": 1,
                    "productSku": "SKU-001",
                    "locationId": 10,
                    "batchNumber": "BATCH-001",
                    "costPrice": 10.00,
                    "quantity": 50,
                    "prices": [
                        {
                            "priceType": null,
                            "price": 10.00,
                            "minQuantity": 5,
                            "minPrice": -12.00
                        }
                    ]
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['prices[0].minPrice']").value("Minimum price must be zero or positive"));
        }
    }

    @Nested
    @DisplayName("Get All Batches Tests")
    class GetAllBatchesTests {

        @Test
        @DisplayName("GET /batches - 200 on valid request")
        void getAllBatches_ValidRequest_Returns200() throws Exception {
            Page<BatchResponse> mockPage = new PageImpl<>(List.of(mock(BatchResponse.class)));
            when(batchService.getAllBatches(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(mockPage);

            mockMvc.perform(get("/api/v1/inventory/batches?page=0&size=10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Batches fetched successfully"))
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        // =====================================================================
        // Listing Params Validations:
        // =====================================================================

        @Test
        @DisplayName("GET /batches - 400 when expiryStartDate is after expiryEndDate")
        void getAllBatches_StartDateAfterEndDate_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/inventory/batches?expiryStartDate=2024-12-31&expiryEndDate=2024-01-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("The expiryStartDate cannot be after the expiryEndDate"));
        }

        @Test
        @DisplayName("GET /batches - 200 when only one date is provided (no exception should be thrown)")
        void getAllBatches_SingleDate_Returns200() throws Exception {
            Page<BatchResponse> mockPage = new PageImpl<>(List.of());
            when(batchService.getAllBatches(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(mockPage);

            mockMvc.perform(get("/api/v1/inventory/batches?expiryEndDate=2024-12-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("Get Batch by ID Tests")
    class GetBatchByIdTests {

        @Test
        @DisplayName("GET /batches/{id} - 200 on valid id")
        void getBatchById_Returns200() throws Exception {
            when(batchService.getBatchById(1L)).thenReturn(mock(BatchResponse.class));

            mockMvc.perform(get("/api/v1/inventory/batches/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Batch fetched successfully"));
        }
    }

    @Nested
    @DisplayName("Update Batch Tests")
    class UpdateBatchTests {

        @Test
        @DisplayName("PUT /batches/{id} - 200 on valid request")
        void updateBatch_ValidPayload_Returns200() throws Exception {
            BatchUpdateRequest request = new BatchUpdateRequest(LocalDate.now(), BigDecimal.valueOf(15.00), BigDecimal.valueOf(100));
            when(batchService.updateBatch(eq(1L), any())).thenReturn(mock(BatchResponse.class));

            mockMvc.perform(put("/api/v1/inventory/batches/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Batch updated successfully"));
        }

        // =====================================================================
        // Update Params Validations:
        // =====================================================================

        @Test
        @DisplayName("PUT /batches/{id} - 400 when costPrice is zero or negative")
        void updateBatch_NegativeCostPrice_Returns400() throws Exception {
            String json = """
                {
                    "costPrice": 0.00
                }
                """;

            mockMvc.perform(put("/api/v1/inventory/batches/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.costPrice").value("Cost price must be positive"));
        }

        @Test
        @DisplayName("PUT /batches/{id} - 400 when quantity is less than 1")
        void updateBatch_QuantityLessThanOne_Returns400() throws Exception {
            String json = """
                {
                    "quantity": -5
                }
                """;

            mockMvc.perform(put("/api/v1/inventory/batches/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.quantity").value("Quantity must be at least 1"));
        }
    }

    @Nested
    @DisplayName("Cancel Batch Tests")
    class CancelBatchTests {

        @Test
        @DisplayName("PATCH /batches/{id}/cancel - 200 on valid id")
        void cancelBatch_Returns200() throws Exception {
            when(batchService.cancelBatch(1L)).thenReturn(mock(BatchResponse.class));

            mockMvc.perform(patch("/api/v1/inventory/batches/1/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Batch cancelled successfully"));
        }
    }

    @Nested
    @DisplayName("Purge Cancelled Batch Tests")
    class PurgeCancelledBatchTests {

        @Test
        @DisplayName("DELETE /batches/{id}/permanent - 200 on valid id")
        void purgeCancelledBatch_Returns200() throws Exception {
            mockMvc.perform(delete("/api/v1/inventory/batches/1/permanent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Batch permanently deleted"));

            verify(batchService).purgeCancelledBatch(1L);
        }
    }
}