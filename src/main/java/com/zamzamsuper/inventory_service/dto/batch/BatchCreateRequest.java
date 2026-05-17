package com.zamzamsuper.inventory_service.dto.batch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.zamzamsuper.inventory_service.dto.ProductPriceRequest;
import com.zamzamsuper.inventory_service.dto.validation.StandaloneBatchCreation;
import com.zamzamsuper.inventory_service.enums.BatchStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BatchCreateRequest(
        @NotNull(groups = StandaloneBatchCreation.class, message = "GRN ID is required when creating a standalone batch")
        Long grnId,

        @NotBlank(message = "Product SKU is required")
        String productSku,

        @NotNull(message = "Location ID is required")
        Long locationId,

        LocalDate expiryDate, // Optional

        @NotBlank(message = "Batch number is required")
        String batchNumber,

        @NotNull(message = "Cost price is required")
        @Positive(message = "Cost price must be positive")
        BigDecimal costPrice,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        BigDecimal quantity,

        BatchStatus status, // Optional, defaults to ACTIVE if not provided

        @NotEmpty(message = "At least one price type must be defined")
        @Valid
        List<ProductPriceRequest> prices
) {
}
