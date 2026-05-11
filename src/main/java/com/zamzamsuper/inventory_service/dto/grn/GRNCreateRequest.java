package com.zamzamsuper.inventory_service.dto.grn;

import java.math.BigDecimal;
import java.util.List;

import com.zamzamsuper.inventory_service.dto.BatchRequest;
import com.zamzamsuper.inventory_service.enums.GRNStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record GRNCreateRequest(
        @NotNull(message = "Supplier ID is required") Long supplierId,
        @NotBlank(message = "Invoice number cannot be empty") String invoiceNum,
        @NotNull(message = "Subtotal is required")
                @Positive(message = "Subtotal must be greater than zero")
                BigDecimal subTotalAmount,
        @PositiveOrZero(message = "Discount cannot be negative") BigDecimal totalDiscount,
        @NotNull(message = "Grand total is required")
                @PositiveOrZero(message = "Grand total must be must be zero or positive")
                BigDecimal grandTotalAmount,
        GRNStatus status, // Optional, defaults to POSTED if not provided
        @NotEmpty(message = "GRN must contain at least one batch")
                @Valid // Critical: This ensures the objects inside the list are also validated
                List<BatchRequest> batches) {}
