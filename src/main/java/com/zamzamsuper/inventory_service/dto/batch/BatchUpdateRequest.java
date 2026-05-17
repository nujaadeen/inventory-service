package com.zamzamsuper.inventory_service.dto.batch;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BatchUpdateRequest(
        LocalDate expiryDate, // Optional

        @Positive(message = "Cost price must be positive")
        BigDecimal costPrice,

        @Min(value = 1, message = "Quantity must be at least 1")
        BigDecimal quantity
) {
}
