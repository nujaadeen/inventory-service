package com.zamzamsuper.inventory_service.dto.success;

import java.time.LocalDateTime;

public record SuccessResponse<T>(
        boolean success,     // Always true for this record
        String message,
        LocalDateTime timestamp,
        T data
) {
    public static <T> SuccessResponse<T> of(String message, T data) {
        return new SuccessResponse<>(true, message, LocalDateTime.now(), data);
    }
}