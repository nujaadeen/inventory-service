package com.zamzamsuper.inventory_service.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zamzamsuper.inventory_service.dto.grn.GRNCreateRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNResponse;
import com.zamzamsuper.inventory_service.dto.grn.GRNUpdateRequest;
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
    public ResponseEntity<GRNResponse> createGRN(@Valid @RequestBody GRNCreateRequest request) {
        log.info("Received request to create GRN: {}", request);
        GRNResponse response = grnService.createGRN(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<GRNResponse>> getAllGRNs(
            @RequestParam(required = false) String invoiceNum,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) GRNStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate endDate,
            Pageable pageable) {

        log.info(
                "REST request to get all GRNs with filters | invoice: {}, supplier: {}, status: {},"
                        + " range: {} to {}",
                invoiceNum,
                supplierId,
                status,
                startDate,
                endDate);

        // 1. Cross-Field Validation: If user provides START, they must have an END (or default it)
        if (startDate != null && endDate == null) {
            endDate = LocalDate.now(); // Default to today
            log.debug("No endDate provided, defaulting to today: {}", endDate);
        }

        // 2. Cross-Field Validation: If user provides END but no START
        if (endDate != null && startDate == null) {
            // Option A: Throw error (Strict)
            throw new IllegalArgumentException("startDate is required when endDate is provided");

            // Option B: Default to 1 month ago (Forgiving)
            // startDate = endDate.minusMonths(1);
        }

        // 3. Logic Check: Ensure dates are in the correct order
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("The startDate cannot be after the endDate");
        }

        // 4. Pass sanitized values to the Service
        Page<GRNResponse> response =
                grnService.getAllGRNs(invoiceNum, supplierId, status, startDate, endDate, pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GRNResponse> getGRNById(@PathVariable Long id) {
        log.info("Fetching GRN with ID: {}", id);
        return ResponseEntity.ok(grnService.getGRNById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GRNResponse> updateGRN(
            @PathVariable Long id, @Valid @RequestBody GRNUpdateRequest request) {
        log.info("Updating GRN ID: {} with data: {}", id, request);
        return ResponseEntity.ok(grnService.updateGRN(id, request));
    }

    // @DeleteMapping("/{id}")
    // public ResponseEntity<Void> deleteGRN(@PathVariable Long id) {
    //     log.warn("Deleting GRN with ID: {}", id);
    //     grnService.cancelGRN(id);
    //     return ResponseEntity.noContent().build();
    // }

    @PatchMapping("/{id}/cancel") // ✅ PATCH for soft delete / status change
    public ResponseEntity<GRNResponse> cancelGRN(@PathVariable Long id) {
        log.info("Cancelling GRN with ID: {}", id);
        return ResponseEntity.ok(grnService.cancelGRN(id));
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> purgeCancelledGRN(@PathVariable Long id) {
        log.warn("Request received for PERMANENT deletion of GRN ID: {}", id);
        grnService.purgeCancelledGRN(id);
        return ResponseEntity.noContent().build();
    }
}
