package com.zamzamsuper.inventory_service.dto;

import java.math.BigDecimal;

import com.zamzamsuper.inventory_service.enums.PriceType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductPriceRequest(
        Long batchId,
        @NotNull(message = "Price type is required") PriceType priceType,
        Boolean active,
        @Min(value = 0, message = "Min quantity cannot be negative") Integer minQuantity,
        @NotNull(message = "Minimum price is required")
                @Positive(message = "Minimum price must be greater than 0")
                BigDecimal price,
        @Positive(message = "Minimum price must be greater than 0") BigDecimal minPrice) {}
