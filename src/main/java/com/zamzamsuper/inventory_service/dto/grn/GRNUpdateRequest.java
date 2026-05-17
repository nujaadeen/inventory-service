package com.zamzamsuper.inventory_service.dto.grn;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record GRNUpdateRequest(
        Long supplierId,

        @NotBlank(message = "Invoice number cannot be empty")
        String invoiceNum,

        @PositiveOrZero(message = "Discount cannot be negative")
        BigDecimal totalDiscount
) {
}
