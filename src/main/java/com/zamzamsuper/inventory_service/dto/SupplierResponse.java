package com.zamzamsuper.inventory_service.dto;

import java.time.LocalDateTime;

public record SupplierResponse(
        Long id, String name, String phone, LocalDateTime createdAt, LocalDateTime updatedAt) {}
