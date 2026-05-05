package com.zamzamsuper.inventory_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SupplierReturnItemResponse(
    Long id,
    Long supplierReturnId,
    Long batchId,
    String batchNumber, 
    Integer qtyReturned,
    BigDecimal unitCostRefunded,
    String reason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}