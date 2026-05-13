package com.zamzamsuper.inventory_service.dto;

import java.math.BigDecimal;

public record StockRequest(
        String productSku, Long locationId, BigDecimal quantityOnHand, BigDecimal reorderLevel) {}
