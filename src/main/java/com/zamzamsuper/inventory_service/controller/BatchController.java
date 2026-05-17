package com.zamzamsuper.inventory_service.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.zamzamsuper.inventory_service.dto.batch.BatchCreateRequest;
import com.zamzamsuper.inventory_service.dto.batch.BatchResponse;
import com.zamzamsuper.inventory_service.dto.batch.BatchUpdateRequest;
import com.zamzamsuper.inventory_service.dto.success.SuccessResponse;
import com.zamzamsuper.inventory_service.dto.validation.StandaloneBatchCreation;
import com.zamzamsuper.inventory_service.enums.BatchStatus;
import com.zamzamsuper.inventory_service.exception.BadRequestException;
import com.zamzamsuper.inventory_service.service.BatchService;

import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/batches")
public class BatchController {

    private final BatchService batchService;

    @PostMapping
    public ResponseEntity<SuccessResponse<BatchResponse>> createBatch(
            // Uses the marker interface to require grnId for standalone batch creation
            @Validated({StandaloneBatchCreation.class, Default.class}) @RequestBody BatchCreateRequest request) {

        log.info("Received request to create standalone Batch: {}", request);
        BatchResponse response = batchService.createBatch(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.of("Batch created successfully", response));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<Page<BatchResponse>>> getAllBatches(
            @RequestParam(required = false) String productSku,
            @RequestParam(required = false) String batchNumber,
            @RequestParam(required = false) BatchStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryStartDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryEndDate,
            Pageable pageable) {

        // Logic Check: Ensure dates are in the correct order if both are provided
        if (expiryStartDate != null && expiryEndDate != null && expiryStartDate.isAfter(expiryEndDate)) {
            throw new BadRequestException("The expiryStartDate cannot be after the expiryEndDate");
        }

        Page<BatchResponse> response = batchService.getAllBatches(
                productSku, batchNumber, status, expiryStartDate, expiryEndDate, pageable);

        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of("Batches fetched successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<BatchResponse>> getBatchById(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of("Batch fetched successfully", batchService.getBatchById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponse<BatchResponse>> updateBatch(
            @PathVariable Long id, @Valid @RequestBody BatchUpdateRequest request) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of("Batch updated successfully", batchService.updateBatch(id, request)));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<SuccessResponse<BatchResponse>> cancelBatch(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of("Batch cancelled successfully", batchService.cancelBatch(id)));
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<SuccessResponse<Void>> purgeCancelledBatch(@PathVariable Long id) {
        batchService.purgeCancelledBatch(id);

        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of("Batch permanently deleted", null));
    }
}