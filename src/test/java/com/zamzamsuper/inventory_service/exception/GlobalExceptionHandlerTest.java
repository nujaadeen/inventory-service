package com.zamzamsuper.inventory_service.exception;

import com.zamzamsuper.inventory_service.controller.GRNController;
import com.zamzamsuper.inventory_service.dto.grn.GRNCreateRequest;
import com.zamzamsuper.inventory_service.service.GRNService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.persistence.EntityNotFoundException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GRNController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GRNService grnService;

    // =====================================================================
    // 1. EntityNotFoundException → 404
    // =====================================================================

    @Nested
    @DisplayName("EntityNotFoundException → 404")
    class EntityNotFoundTests {

        @Test
        @DisplayName("GET /grns/{id} - Should return 404 when GRN not found")
        void getGRNById_NotFound_Returns404() throws Exception {
            when(grnService.getGRNById(99L))
                    .thenThrow(new EntityNotFoundException("GRN not found with id: 99"));

            mockMvc.perform(get("/api/v1/inventory/grns/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("GRN not found with id: 99"))
                    .andExpect(jsonPath("$.details").isEmpty());
        }
    }

    // =====================================================================
    // 2. StockConflictException → 409
    // =====================================================================

    @Nested
    @DisplayName("StockConflictException → 409")
    class StockConflictTests {

        @Test
        @DisplayName("POST /grns - Should return 409 on stock conflict")
        void createGRN_StockConflict_Returns409() throws Exception {
            when(grnService.createGRN(any(GRNCreateRequest.class)))
                    .thenThrow(new StockConflictException("Batch SKU-001 already exists in this location"));

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validGRNCreateJson()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message")
                            .value("Stock conflict: Cannot revert quantity for SKU Batch SKU-001 already exists in this location. Inventory has already been utilized."));
        }
    }

    // =====================================================================
    // 3. InvalidStatusException → 409
    // =====================================================================

    @Nested
    @DisplayName("InvalidStatusException → 409")
    class InvalidGRNStatusTests {

        @Test
        @DisplayName("PATCH /grns/{id}/cancel - Should return 409 when GRN is already cancelled")
        void cancelGRN_AlreadyCancelled_Returns409() throws Exception {
            when(grnService.cancelGRN(1L))
                    .thenThrow(new InvalidStatusException("Cannot cancel a GRN that is already cancelled"));

            mockMvc.perform(patch("/api/v1/inventory/grns/1/cancel"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Cannot cancel a GRN that is already cancelled"));
        }

        @Test
        @DisplayName("DELETE /grns/{id}/permanent - Should return 409 when GRN is not in cancelled state")
        void purgeGRN_NotCancelled_Returns409() throws Exception {
            // doThrow is used for void methods
            org.mockito.Mockito.doThrow(new InvalidStatusException("Only cancelled GRNs can be permanently deleted"))
                    .when(grnService).purgeCancelledGRN(1L);

            mockMvc.perform(delete("/api/v1/inventory/grns/1/permanent"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Only cancelled GRNs can be permanently deleted"));
        }
    }

    // =====================================================================
    // 4. FinancialMismatchException → 400
    // =====================================================================

    @Nested
    @DisplayName("FinancialMismatchException → 400")
    class FinancialMismatchTests {

        @Test
        @DisplayName("POST /grns - Should return 400 when grand total doesn't match subtotal minus discount")
        void createGRN_FinancialMismatch_Returns400() throws Exception {
            when(grnService.createGRN(any(GRNCreateRequest.class)))
                    .thenThrow(new FinancialMismatchException(
                            "Grand total 500.00 does not match subTotal 600.00 minus discount 50.00"));

            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validGRNCreateJson()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message")
                            .value("Grand total 500.00 does not match subTotal 600.00 minus discount 50.00"));
        }
    }

    // =====================================================================
    // 5. IllegalStateException → 409
    // =====================================================================

    @Nested
    @DisplayName("IllegalStateException → 409")
    class IllegalStateTests {

        @Test
        @DisplayName("PATCH /grns/{id}/cancel - Should return 409 on illegal state")
        void cancelGRN_IllegalState_Returns409() throws Exception {
            when(grnService.cancelGRN(1L))
                    .thenThrow(new IllegalStateException("GRN is locked and cannot be modified"));

            mockMvc.perform(patch("/api/v1/inventory/grns/1/cancel"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("GRN is locked and cannot be modified"));
        }
    }

    // =====================================================================
    // 6. HttpMessageNotReadableException → 400 (Malformed JSON)
    // =====================================================================

    @Nested
    @DisplayName("Malformed JSON → 400")
    class MalformedJsonTests {

        @Test
        @DisplayName("POST /grns - Should return 400 on completely malformed JSON")
        void createGRN_MalformedJson_Returns400() throws Exception {
            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ this is not valid json }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message")
                            .value("Invalid input format. Please check your JSON syntax and data types."));
        }

        @Test
        @DisplayName("POST /grns - Should return 400 when field has wrong data type")
        void createGRN_WrongDataType_Returns400() throws Exception {
            // supplierId is a Long but we pass a string
            String json = """
                {
                    "supplierId": "not-a-number",
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
                    .andExpect(jsonPath("$.message")
                            .value("Invalid input format. Please check your JSON syntax and data types."));
        }

        @Test
        @DisplayName("POST /grns - Should return 400 when body is empty string")
        void createGRN_EmptyBody_Returns400() throws Exception {
            mockMvc.perform(post("/api/v1/inventory/grns")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message")
                            .value("Invalid input format. Please check your JSON syntax and data types."));
        }
    }

    // =====================================================================
    // 7. MethodArgumentTypeMismatchException → 400 (Path/Query param type mismatch)
    // =====================================================================

    @Nested
    @DisplayName("MethodArgumentTypeMismatchException → 400")
    class TypeMismatchTests {

        @Test
        @DisplayName("GET /grns/{id} - Should return 400 when id is not a number")
        void getGRNById_NonNumericId_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/inventory/grns/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    // message format: "Parameter 'id' expects a value of type 'Long', but received 'abc'"
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Parameter 'id' expects a value of type")));
        }

        @Test
        @DisplayName("GET /grns - Should return 400 when supplierId query param is not a number")
        void getAllGRNs_NonNumericSupplierId_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/inventory/grns?supplierId=abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Parameter 'supplierId' expects a value of type")));
        }

        @Test
        @DisplayName("GET /grns - Should return 400 when status query param is an invalid enum value")
        void getAllGRNs_InvalidStatusEnum_Returns400() throws Exception {
            mockMvc.perform(get("/api/v1/inventory/grns?status=INVALID_STATUS"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Parameter 'status' expects a value of type")));
        }
    }

    // =====================================================================
    // 8. Unhandled Exception → 500
    // =====================================================================

    @Nested
    @DisplayName("Unhandled Exception → 500")
    class UnhandledExceptionTests {

        @Test
        @DisplayName("GET /grns/{id} - Should return 500 on unexpected runtime error")
        void getGRNById_UnexpectedError_Returns500() throws Exception {
            when(grnService.getGRNById(1L))
                    .thenThrow(new RuntimeException("Unexpected DB connection failure"));

            mockMvc.perform(get("/api/v1/inventory/grns/1"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("An internal server error occurred"));
        }
    }

    // =====================================================================
    // Helper Methods
    // =====================================================================

    private String validGRNCreateJson() {
        return """
            {
                "supplierId": 1,
                "invoiceNum": "INV-001",
                "subTotalAmount": 100.00,
                "totalDiscount": 0.00,
                "grandTotalAmount": 100.00,
                "batches": []
            }
            """;
    }
}