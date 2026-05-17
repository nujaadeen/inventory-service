package com.zamzamsuper.inventory_service.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.zamzamsuper.inventory_service.dto.grn.GRNCreateRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNResponse;
import com.zamzamsuper.inventory_service.dto.grn.GRNUpdateRequest;
import com.zamzamsuper.inventory_service.dto.success.SuccessResponse;
import com.zamzamsuper.inventory_service.enums.GRNStatus;
import com.zamzamsuper.inventory_service.service.GRNService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/grns")
public class GRNController {

    private final GRNService grnService;

    @PostMapping
    public ResponseEntity<SuccessResponse<GRNResponse>> createGRN(@Valid @RequestBody GRNCreateRequest request) {
        log.info("Received request to create GRN: {}", request);
        GRNResponse response = grnService.createGRN(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.of("GRN created successfully", response));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<Page<GRNResponse>>> getAllGRNs(
            @RequestParam(required = false) String invoiceNum,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) GRNStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {

        if (startDate != null && endDate == null) {
            endDate = LocalDate.now();
        }

        if (endDate != null && startDate == null) {
            throw new IllegalArgumentException("startDate is required when endDate is provided");
        }

        if (startDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("The startDate cannot be after the endDate");
        }

        Page<GRNResponse> response = grnService.getAllGRNs(invoiceNum, supplierId, status, startDate, endDate, pageable);

        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of("GRNs fetched successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponse<GRNResponse>> getGRNById(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of("GRN fetched successfully", grnService.getGRNById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponse<GRNResponse>> updateGRN(
            @PathVariable Long id, @Valid @RequestBody GRNUpdateRequest request) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of("GRN updated successfully", grnService.updateGRN(id, request)));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<SuccessResponse<GRNResponse>> cancelGRN(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of("GRN cancelled successfully", grnService.cancelGRN(id)));
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<SuccessResponse<Void>> purgeCancelledGRN(@PathVariable Long id) {
        grnService.purgeCancelledGRN(id);

        return ResponseEntity.status(HttpStatus.OK)
                .body(SuccessResponse.of("GRN permanently deleted", null));
    }
}