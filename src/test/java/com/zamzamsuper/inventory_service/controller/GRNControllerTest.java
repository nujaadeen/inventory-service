package com.zamzamsuper.inventory_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zamzamsuper.inventory_service.dto.grn.GRNCreateRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNResponse;
import com.zamzamsuper.inventory_service.dto.grn.GRNUpdateRequest;
import com.zamzamsuper.inventory_service.exception.GlobalExceptionHandler;
import com.zamzamsuper.inventory_service.service.GRNService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GRNController.class)
@Import(GlobalExceptionHandler.class)
class GRNControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GRNService grnService;

    @Nested
    @DisplayName("Create GRN Tests")
    class CreateGRNTests {

        @Test
        @DisplayName("POST /grns - 201 on valid payload")
        void createGRN_ValidPayload_Returns201() throws Exception {
            when(grnService.createGRN(any(GRNCreateRequest.class))).thenReturn(mock(GRNResponse.class));

            String json = """
                {
                    "supplierId": 1,
                    "invoiceNum": "INV-001",
                    "subTotalAmount": 100.00,
                    "totalDiscount": 0.00,
                    "grandTotalAmount": 100.00,
                    "batches": []
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("GRN created successfully"))
                    .andExpect(jsonPath("$.data").exists());
        }

        // =====================================================================
        // Creation Params Validations:
        // =====================================================================

        // ── GRN Request Body Validation ───────────────────────────────────────────

        @Test
        @DisplayName("POST /grns - 400 when supplierId is missing")
        void createGRN_MissingSupplierId_Returns400() throws Exception {
            String json = """
                {
                    "invoiceNum": "INV-001",
                    "subTotalAmount": 100.00,
                    "grandTotalAmount": 100.00,
                    "batches": []
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Validation Failed"))
                    .andExpect(jsonPath("$.details.supplierId").value("Supplier ID is required"));
        }

        @Test
        @DisplayName("POST /grns - 400 when invoiceNum is blank")
        void createGRN_BlankInvoiceNum_Returns400() throws Exception {
            String json = """
                {
                    "supplierId": 1,
                    "invoiceNum": "   ",
                    "subTotalAmount": 100.00,
                    "grandTotalAmount": 100.00,
                    "batches": []
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.invoiceNum").value("Invoice number cannot be empty"));
        }

        @Test
        @DisplayName("POST /grns - 400 when subTotalAmount is missing")
        void createGRN_MissingSubTotal_Returns400() throws Exception {
            String json = """
                {
                    "supplierId": 1,
                    "invoiceNum": "INV-001",
                    "grandTotalAmount": 100.00,
                    "batches": []
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.subTotalAmount").value("Subtotal is required"));
        }

        @Test
        @DisplayName("POST /grns - 400 when subTotalAmount is zero (must be positive)")
        void createGRN_ZeroSubTotal_Returns400() throws Exception {
            String json = """
                {
                    "supplierId": 1,
                    "invoiceNum": "INV-001",
                    "subTotalAmount": 0.00,
                    "grandTotalAmount": 0.00,
                    "batches": []
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.subTotalAmount").value("Subtotal must be greater than zero"));
        }

        @Test
        @DisplayName("POST /grns - 400 when subTotalAmount is negative")
        void createGRN_NegativeSubTotal_Returns400() throws Exception {
            String json = """
                {
                    "supplierId": 1,
                    "invoiceNum": "INV-001",
                    "subTotalAmount": -10.00,
                    "grandTotalAmount": 100.00,
                    "batches": []
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.subTotalAmount").value("Subtotal must be greater than zero"));
        }

        @Test
        @DisplayName("POST /grns - 400 when totalDiscount is negative")
        void createGRN_NegativeDiscount_Returns400() throws Exception {
            String json = """
                {
                    "supplierId": 1,
                    "invoiceNum": "INV-001",
                    "subTotalAmount": 100.00,
                    "totalDiscount": -5.00,
                    "grandTotalAmount": 100.00,
                    "batches": []
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.totalDiscount").value("Discount cannot be negative"));
        }

        @Test
        @DisplayName("POST /grns - 400 when grandTotalAmount is missing")
        void createGRN_MissingGrandTotal_Returns400() throws Exception {
            String json = """
                {
                    "supplierId": 1,
                    "invoiceNum": "INV-001",
                    "subTotalAmount": 100.00,
                    "batches": []
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.grandTotalAmount").value("Grand total is required"));
        }

        @Test
        @DisplayName("POST /grns - 400 when grandTotalAmount is negative")
        void createGRN_NegativeGrandTotal_Returns400() throws Exception {
            String json = """
                {
                    "supplierId": 1,
                    "invoiceNum": "INV-001",
                    "subTotalAmount": 100.00,
                    "grandTotalAmount": -1.00,
                    "batches": []
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.grandTotalAmount").value("Grand total must be must be zero or positive"));
        }

        @Test
        @DisplayName("POST /grns - 400 when batches list is null")
        void createGRN_NullBatches_Returns400() throws Exception {
            String json = """
                {
                    "supplierId": 1,
                    "invoiceNum": "INV-001",
                    "subTotalAmount": 100.00,
                    "grandTotalAmount": 100.00
                }
                """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.batches").value("Batches list must be provided (can be empty)"));
        }

        @Test
        @DisplayName("POST /grns - 400 when multiple required fields are missing")
        void createGRN_MultipleFieldsMissing_Returns400() throws Exception {
            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Validation Failed"))
                    .andExpect(jsonPath("$.details.supplierId").exists())
                    .andExpect(jsonPath("$.details.invoiceNum").exists())
                    .andExpect(jsonPath("$.details.subTotalAmount").exists())
                    .andExpect(jsonPath("$.details.grandTotalAmount").exists())
                    .andExpect(jsonPath("$.details.batches").exists());
        }

        // ── Nested Batch Request Body Validation ───────────────────────────────────────────

        @Test
        @DisplayName("POST /grns - 400 when batch is missing productSku")
        void createGRN_BatchMissingProductSku_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "locationId": 10,
                                "batchNumber": "BATCH-001",
                                "costPrice": 10.00,
                                "quantity": 5,
                                "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].productSku']")
                            .value("Product SKU is required"));
        }

        @Test
        @DisplayName("POST /grns - 400 when batch is missing locationId")
        void createGRN_BatchMissingLocationId_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "batchNumber": "BATCH-001",
                                "costPrice": 10.00,
                                "quantity": 5,
                                "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].locationId']")
                            .value("Location ID is required"));
        }

        @Test
        @DisplayName("POST /grns - 400 when batch is missing batchNumber")
        void createGRN_BatchMissingBatchNumber_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "locationId": 10,
                                "costPrice": 10.00,
                                "quantity": 5,
                                "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].batchNumber']")
                            .value("Batch number is required"));
        }

        @Test
        @DisplayName("POST /grns - 400 when batch is missing costPrice")
        void createGRN_BatchMissingCostPrice_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "locationId": 10,
                                "batchNumber": "BATCH-001",
                                "quantity": 5,
                                "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].costPrice']")
                            .value("Cost price is required"));
        }

        @Test
        @DisplayName("POST /grns - 400 when batch costPrice is zero or negative")
        void createGRN_BatchNonPositiveCostPrice_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "locationId": 10,
                                "batchNumber": "BATCH-001",
                                "costPrice": 0.00,
                                "quantity": 5,
                                "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].costPrice']")
                            .value("Cost price must be positive"));
        }

        @Test
        @DisplayName("POST /grns - 400 when batch is missing quantity")
        void createGRN_BatchMissingQuantity_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "locationId": 10,
                                "batchNumber": "BATCH-001",
                                "costPrice": 10.00,
                                "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].quantity']")
                            .value("Quantity is required"));
        }

        @Test
        @DisplayName("POST /grns - 400 when batch quantity is less than 1")
        void createGRN_BatchQuantityLessThanOne_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "locationId": 10,
                                "batchNumber": "BATCH-001",
                                "costPrice": 10.00,
                                "quantity": 0,
                                "prices": [{ "priceType": "RETAIL", "price": 15.00 }]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].quantity']")
                            .value("Quantity must be at least 1"));
        }

        @Test
        @DisplayName("POST /grns - 400 when batch has empty prices list")
        void createGRN_BatchEmptyPrices_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "locationId": 10,
                                "batchNumber": "BATCH-001",
                                "costPrice": 10.00,
                                "quantity": 5,
                                "prices": []
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].prices']")
                            .value("At least one price type must be defined"));
        }

        // ── Deeply Nested ProductPrice Request Body Validation ────────────────────────

        @Test
        @DisplayName("POST /grns - 400 when price entry is missing priceType")
        void createGRN_PriceMissingPriceType_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "locationId": 10,
                                "batchNumber": "BATCH-001",
                                "costPrice": 10.00,
                                "quantity": 5,
                                "prices": [{ "price": 15.00 }]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].prices[0].priceType']")
                            .value("Price type is required"));
        }

        @Test
        @DisplayName("POST /grns - 400 when minQuantity is negative")
        void createGRN_PriceNegativeMinQuantity_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "locationId": 10,
                                "batchNumber": "BATCH-001",
                                "costPrice": 10.00,
                                "quantity": 5,
                                "prices": [
                                    { "priceType": "RETAIL", "price": 15.00, "minQuantity": -1 }
                                ]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].prices[0].minQuantity']")
                            .value("Min quantity cannot be negative"));
        }

        @Test
        @DisplayName("POST /grns - 400 when price entry is missing price field")
        void createGRN_PriceMissingPriceField_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "locationId": 10,
                                "batchNumber": "BATCH-001",
                                "costPrice": 10.00,
                                "quantity": 5,
                                "prices": [{ "priceType": "RETAIL" }]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].prices[0].price']")
                            .value("Price is required"));
        }

        @Test
        @DisplayName("POST /grns - 400 ")
        void createGRN_PriceIsZero_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "locationId": 10,
                                "batchNumber": "BATCH-001",
                                "costPrice": 10.00,
                                "quantity": 5,
                                "prices": [{ "priceType": "RETAIL", "price": 0.00 }]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].prices[0].price']")
                            .value("Price must be greater than 0"));
        }

        @Test
        @DisplayName("POST /grns - 400 when minPrice is negative")
        void createGRN_NegativeMinPrice_Returns400() throws Exception {
            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "locationId": 10,
                                "batchNumber": "BATCH-001",
                                "costPrice": 10.00,
                                "quantity": 5,
                                "prices": [
                                    { "priceType": "RETAIL", "price": 15.00, "minPrice": -10.00 }
                                ]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details['batches[0].prices[0].minPrice']")
                            .value("Minimum price must be zero or positive"));
        }

        @Test
        @DisplayName("POST /grns - 201 when valid batch with multiple prices")
        void createGRN_ValidBatchWithMultiplePrices_Returns201() throws Exception {
            when(grnService.createGRN(any(GRNCreateRequest.class))).thenReturn(mock(GRNResponse.class));

            String json = """
                    {
                        "supplierId": 1,
                        "invoiceNum": "INV-001",
                        "subTotalAmount": 100.00,
                        "grandTotalAmount": 100.00,
                        "batches": [
                            {
                                "productSku": "SKU-001",
                                "locationId": 10,
                                "batchNumber": "BATCH-001",
                                "costPrice": 10.00,
                                "quantity": 5,
                                "prices": [
                                    { "priceType": "RETAIL", "price": 15.00 },
                                    { "priceType": "WHOLESALE", "price": 12.00, "minQuantity": 10 }
                                ]
                            }
                        ]
                    }
                    """;

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("Get All GRN Tests")
    class GetAllGRNTests {

        @Test
        @DisplayName("GET /grns - 200 on valid request")
        void getAllGRNs_ValidRequest_Returns200() throws Exception {
            Page<GRNResponse> mockPage = new PageImpl<>(List.of(mock(GRNResponse.class)));
            when(grnService.getAllGRNs(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(mockPage);

            mockMvc.perform(get("/api/v1/inventory/grns?page=0&size=10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("GRNs fetched successfully"))
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        // =====================================================================
        // listing Params Validations:
        // =====================================================================

        @Test
        @DisplayName("GET /grns - 400 when endDate is provided without startDate")
        void getAllGRNs_EndDateWithoutStartDate_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/inventory/grns?endDate=2024-12-31"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("startDate is required when endDate is provided"));
        }

        @Test
        @DisplayName("GET /grns - 400 when startDate is after endDate")
        void getAllGRNs_StartDateAfterEndDate_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/inventory/grns?startDate=2024-12-31&endDate=2024-01-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("The startDate cannot be after the endDate"));
        }

        @Test
        @DisplayName("GET /grns - 200 when only startDate is provided (endDate defaults to today)")
        void getAllGRNs_OnlyStartDate_Returns200() throws Exception {
            Page<GRNResponse> mockPage = new PageImpl<>(List.of());
            when(grnService.getAllGRNs(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(mockPage);

            mockMvc.perform(get("/api/v1/inventory/grns?startDate=2024-01-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("GET /grns - 200 when both startDate and endDate are the same day")
        void getAllGRNs_StartDateEqualsEndDate_Returns200() throws Exception {
            Page<GRNResponse> mockPage = new PageImpl<>(List.of());
            when(grnService.getAllGRNs(any(), any(), any(), any(), any(), any(Pageable.class))).thenReturn(mockPage);

            mockMvc.perform(get("/api/v1/inventory/grns?startDate=2024-06-01&endDate=2024-06-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("Get GRN by ID Tests")
    class GetGRNByIdTests {

        @Test
        @DisplayName("GET /grns/{id} - 200 on valid id")
        void getGRNById_Returns200() throws Exception {
            when(grnService.getGRNById(1L)).thenReturn(mock(GRNResponse.class));

            mockMvc.perform(get("/api/v1/inventory/grns/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("GRN fetched successfully"));
        }
    }

    @Nested
    @DisplayName("Update GRN Tests")
    class UpdateGRNTests {

        @Test
        @DisplayName("PUT /grns/{id} - 200 on valid request")
        void updateGRN_ValidPayload_Returns200() throws Exception {
            GRNUpdateRequest request = new GRNUpdateRequest(1L, "INV-123", BigDecimal.ZERO);
            when(grnService.updateGRN(eq(1L), any())).thenReturn(mock(GRNResponse.class));

            mockMvc.perform(put("/api/v1/inventory/grns/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("GRN updated successfully"));
        }

        // =====================================================================
        // Update Params Validations:
        // =====================================================================

        @Test
        @DisplayName("PUT /grns/{id} - 400 when invoiceNum is blank")
        void updateGRN_BlankInvoiceNum_Returns400() throws Exception {
            String json = """
                {
                    "supplierId": 1,
                    "invoiceNum": "",
                    "totalDiscount": 0.00
                }
                """;

            mockMvc.perform(put("/api/v1/inventory/grns/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.invoiceNum").value("Invoice number cannot be empty"));
        }

        @Test
        @DisplayName("PUT /grns/{id} - 400 when totalDiscount is negative")
        void updateGRN_NegativeDiscount_Returns400() throws Exception {
            String json = """
                {
                    "supplierId": 1,
                    "invoiceNum": "INV-123",
                    "totalDiscount": -10.00
                }
                """;

            mockMvc.perform(put("/api/v1/inventory/grns/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.totalDiscount").value("Discount cannot be negative"));
        }

        @Test
        @DisplayName("PUT /grns/{id} - 400 when entire body is empty")
        void updateGRN_EmptyBody_Returns400() throws Exception {
            mockMvc.perform(put("/api/v1/inventory/grns/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.details.invoiceNum").exists());
        }
    }

    @Nested
    @DisplayName("Cancel GRN Tests")
    class CancelGRNTests {

        @Test
        @DisplayName("PATCH /grns/{id}/cancel - 200 on valid id")
        void cancelGRN_Returns200() throws Exception {
            when(grnService.cancelGRN(1L)).thenReturn(mock(GRNResponse.class));

            mockMvc.perform(patch("/api/v1/inventory/grns/1/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("GRN cancelled successfully"));
        }
    }

    @Nested
    @DisplayName("Purge Cancelled GRN Tests")
    class PurgeCancelledGRNTests {

        @Test
        @DisplayName("DELETE /grns/{id}/permanent - 200 on valid id")
        void purgeCancelledGRN_Returns200() throws Exception {
            mockMvc.perform(delete("/api/v1/inventory/grns/1/permanent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("GRN permanently deleted"));

            verify(grnService).purgeCancelledGRN(1L);
        }
    }
}