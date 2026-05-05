package com.zamzamsuper.inventory_service.dto.grn;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.zamzamsuper.inventory_service.dto.BatchResponse;
import com.zamzamsuper.inventory_service.enums.GRNStatus;

public record GRNResponse(
    Long id,
    Long supplierId,
    String supplierName,
    String invoiceNum,
    BigDecimal subTotalAmount,
    BigDecimal totalDiscount,
    BigDecimal grandTotalAmount,
    GRNStatus status,
    List<BatchResponse> batches,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}