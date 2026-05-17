package com.zamzamsuper.inventory_service.exception;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
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
    @SuppressWarnings("unused")
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getMessage(), null));
    }

    // 2. Handle Stock Conflicts (409 Conflict)
    @SuppressWarnings("unused")
    @ExceptionHandler(StockConflictException.class)
    public ResponseEntity<ErrorResponse> handleStockConflict(StockConflictException ex) {
        log.warn("Inventory Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getMessage(), null));
    }

    // 3. Handle Invalid Status Transitions (409 Conflict)
    @SuppressWarnings("unused")
    @ExceptionHandler(InvalidGRNStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatus(InvalidGRNStatusException ex) {
        log.warn("Invalid State Transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getMessage(), null));
    }

    // 4. Handle Financial Validation Failures (400 Bad Request)
    @SuppressWarnings("unused")
    @ExceptionHandler(FinancialMismatchException.class)
    public ResponseEntity<ErrorResponse> handleFinancialMismatch(FinancialMismatchException ex) {
        log.warn("Financial Validation Failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ex.getMessage(), null));
    }

    // 5. Handle Bean Validation Errors (400 Bad Request)
    @SuppressWarnings("unused")
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        err -> err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("Validation Failed", details));
    }

    // 6. Keep IllegalStateException as a fallback for other business logic (409)
    @SuppressWarnings("unused")
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleGeneralBusinessConflict(IllegalStateException ex) {
        log.warn("Generic Business conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getMessage(), null));
    }

    // 7. Handle Malformed JSON (400 Bad Request)
    @SuppressWarnings("unused")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());
        String errorMessage = "Invalid input format. Please check your JSON syntax and data types.";

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(errorMessage, null));
    }

    // 8. Handle Type Mismatches (e.g., passing string for integer)
    @SuppressWarnings("unused")
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String type = (ex.getRequiredType() != null) ? ex.getRequiredType().getSimpleName() : "unknown";
        String message = String.format("Parameter '%s' expects a value of type '%s', but received '%s'",
                ex.getName(), type, ex.getValue());

        log.warn("Parameter type mismatch: {}", message);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(message, null));
    }

    // 9. Handle Unexpected Errors (500)
    @SuppressWarnings("unused")
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("An internal server error occurred", null));
    }
}