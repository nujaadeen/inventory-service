package com.zamzamsuper.inventory_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockResponse(
        Long id,
        String productSku,
        Long locationId,
        String locationName,
        BigDecimal quantityOnHand,
        BigDecimal reorderLevel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
