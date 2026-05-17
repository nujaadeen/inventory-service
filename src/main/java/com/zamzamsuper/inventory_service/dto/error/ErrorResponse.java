package com.zamzamsuper.inventory_service.dto.error;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        boolean success,     // Always false for this record
        String message,
        LocalDateTime timestamp,
        Map<String, String> details
) {
    public static ErrorResponse of(String message, Map<String, String> details) {
        return new ErrorResponse(false, message, LocalDateTime.now(), details);
    }
}