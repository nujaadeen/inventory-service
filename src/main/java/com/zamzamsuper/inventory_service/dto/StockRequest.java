package com.zamzamsuper.inventory_service.dto;

public record StockRequest(
    String productSku,
    Long locationId,
    Integer quantityOnHand,
    Integer reorderLevel
) {}