package com.zamzamsuper.inventory_service.dto;

import com.zamzamsuper.inventory_service.enums.AdjustmentType;

public record StockAdjustmentRequest(
    Long batchId,
    String staffId,
    AdjustmentType adjustmentType,
    Integer quantity,
    String reason
) {}