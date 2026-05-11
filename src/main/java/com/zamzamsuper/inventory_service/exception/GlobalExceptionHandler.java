package com.zamzamsuper.inventory_service.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.zamzamsuper.inventory_service.dto.error.ErrorResponse;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Handle Resource Not Found (404)
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    // 2. Handle Stock Conflicts (409 Conflict)
    @ExceptionHandler(StockConflictException.class)
    public ResponseEntity<ErrorResponse> handleStockConflict(StockConflictException ex) {
        log.warn("Inventory Conflict: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    // 3. Handle Invalid Status Transitions (409 Conflict)
    @ExceptionHandler(InvalidGRNStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatus(InvalidGRNStatusException ex) {
        log.warn("Invalid State Transition: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    // 4. Handle Financial Validation Failures (400 Bad Request)
    @ExceptionHandler(FinancialMismatchException.class)
    public ResponseEntity<ErrorResponse> handleFinancialMismatch(FinancialMismatchException ex) {
        log.warn("Financial Validation Failed: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    // 5. Handle Bean Validation Errors (400 Bad Request)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new HashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(err -> details.put(err.getField(), err.getDefaultMessage()));
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Failed", details);
    }

    // 6. Keep IllegalStateException as a fallback for other business logic (409)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleGeneralBusinessConflict(IllegalStateException ex) {
        log.warn("Generic Business conflict: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    // 7. Handle Malformed JSON (400 Bad Request)
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());

        String errorMessage =
                "Invalid input format. Please check your JSON syntax and data types (e.g., numbers"
                        + " should not contain letters).";

        // In production, you might want to extract the specific field name from the exception,
        // but a clear general message is a great start.
        return buildResponse(HttpStatus.BAD_REQUEST, errorMessage, null);
    }

    // 8. Handle Type Mismatches (e.g., passing "COMPLETED" for an Enum that doesn't have it)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        Class<?> requiredType = ex.getRequiredType();

        String type = (requiredType != null) ? requiredType.getSimpleName() : "unknown";
        Object value = ex.getValue();

        String message =
                String.format(
                        "Parameter '%s' expects a value of type '%s', but received '%s'",
                        name, type, value);

        log.warn("Parameter type mismatch: {}", message);

        return buildResponse(HttpStatus.BAD_REQUEST, message, null);
    }

    // 9. Handle Unexpected Errors (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred", null);
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String message, Map<String, String> details) {
        ErrorResponse error =
                new ErrorResponse(status.value(), message, LocalDateTime.now(), details);
        return new ResponseEntity<>(error, status);
    }
}
