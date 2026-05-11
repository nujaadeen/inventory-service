package com.zamzamsuper.inventory_service.dto;

import java.time.LocalDateTime;

public record LocationResponse(
        Long id, String name, Boolean active, LocalDateTime createdAt, LocalDateTime updatedAt) {}
