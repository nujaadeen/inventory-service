package com.zamzamsuper.inventory_service.dto;

import java.math.BigDecimal;

public record SupplierReturnItemRequest(
        Long supplierReturnId,
        Long batchId,
        Integer qtyReturned,
        BigDecimal unitCostRefunded,
        String reason) {}
