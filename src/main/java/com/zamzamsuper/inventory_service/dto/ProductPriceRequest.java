package com.zamzamsuper.inventory_service.dto;

import java.math.BigDecimal;

import com.zamzamsuper.inventory_service.dto.validation.StandalonePriceCreation;
import com.zamzamsuper.inventory_service.enums.PriceType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductPriceRequest(

        @NotNull(groups = StandalonePriceCreation.class, message = "Batch ID is required when creating a standalone price")
        Long batchId,

        @NotNull(message = "Price type is required")
        PriceType priceType,

        Boolean active,

        @Min(value = 0, message = "Min quantity cannot be negative")
        Integer minQuantity,

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be greater than 0")
        BigDecimal price,

        @PositiveOrZero(message = "Minimum price must be zero or positive")
        BigDecimal minPrice
) {
}
