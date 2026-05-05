package com.zamzamsuper.inventory_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.zamzamsuper.inventory_service.enums.ReturnStatus;

public record SupplierReturnRequest(
    Long supplierId,
    Long originalGrnId,
    LocalDate returnDate,
    BigDecimal totalRefundValue,
    ReturnStatus returnStatus
) {}