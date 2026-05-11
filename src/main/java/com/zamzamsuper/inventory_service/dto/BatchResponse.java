package com.zamzamsuper.inventory_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.zamzamsuper.inventory_service.enums.BatchStatus;

public record BatchResponse(
        Long id,
        Long stockId,
        Long grnId,
        LocalDate expiryDate,
        String batchNumber,
        BigDecimal costPrice,
        Integer quantity,
        BatchStatus status,
        List<ProductPriceResponse> prices,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
