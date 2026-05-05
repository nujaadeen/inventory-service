package com.zamzamsuper.inventory_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.zamzamsuper.inventory_service.enums.PriceType;

public record ProductPriceResponse(
    Long id,
    Long batchId,
    String batchNumber, 
    PriceType priceType,
    Boolean active,
    Integer minQuantity,
    BigDecimal price,
    BigDecimal minPrice,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}