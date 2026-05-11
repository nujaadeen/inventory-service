package com.zamzamsuper.inventory_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.zamzamsuper.inventory_service.enums.ReturnStatus;

public record SupplierReturnResponse(
        Long id,
        Long supplierId,
        String supplierName,
        Long originalGrnId,
        LocalDate returnDate,
        BigDecimal totalRefundValue,
        ReturnStatus returnStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
