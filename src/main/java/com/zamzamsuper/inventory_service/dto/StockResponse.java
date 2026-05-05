package com.zamzamsuper.inventory_service.dto;

import java.time.LocalDateTime;

public record StockResponse(
    Long id,
    String productSku,
    Long locationId,
    String locationName,
    Integer quantityOnHand,
    Integer reorderLevel,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}