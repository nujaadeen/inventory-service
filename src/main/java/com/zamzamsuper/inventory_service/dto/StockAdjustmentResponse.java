package com.zamzamsuper.inventory_service.dto;

import java.time.LocalDateTime;

import com.zamzamsuper.inventory_service.enums.AdjustmentType;

public record StockAdjustmentResponse(
        Long id,
        Long batchId,
        String batchNumber,
        String staffId,
        AdjustmentType adjustmentType,
        Integer quantity,
        String reason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
