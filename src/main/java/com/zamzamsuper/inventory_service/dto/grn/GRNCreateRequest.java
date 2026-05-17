package com.zamzamsuper.inventory_service.dto.grn;

import java.math.BigDecimal;
import java.util.List;

import com.zamzamsuper.inventory_service.dto.batch.BatchCreateRequest;
import com.zamzamsuper.inventory_service.enums.GRNStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record GRNCreateRequest(
        @NotNull(message = "Supplier ID is required")
        Long supplierId,

        @NotBlank(message = "Invoice number cannot be empty")
        String invoiceNum,

        @NotNull(message = "Subtotal is required")
        @Positive(message = "Subtotal must be greater than zero")
        BigDecimal subTotalAmount,

        @PositiveOrZero(message = "Discount cannot be negative")
        BigDecimal totalDiscount,

        @NotNull(message = "Grand total is required")
        @PositiveOrZero(message = "Grand total must be must be zero or positive")
        BigDecimal grandTotalAmount,

        GRNStatus status, // Optional, defaults to POSTED if not provided

        @NotNull(message = "Batches list must be provided (can be empty)")
        @Valid // This ensures the objects inside the list are also validated if the list is not empty
        List<BatchCreateRequest> batches
) {
}
