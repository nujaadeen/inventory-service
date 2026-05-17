package com.zamzamsuper.inventory_service.dto.grn;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record GRNUpdateRequest(
        @NotNull(message = "Supplier ID is required")
        Long supplierId,

        @NotBlank(message = "Invoice number cannot be empty")
        String invoiceNum,

        @PositiveOrZero(message = "Discount cannot be negative")
        BigDecimal totalDiscount
) {
}
