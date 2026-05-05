package com.zamzamsuper.inventory_service.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RestController;

import com.zamzamsuper.inventory_service.dto.grn.GRNCreateRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNResponse;
import com.zamzamsuper.inventory_service.dto.grn.GRNUpdateRequest;
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
    public ResponseEntity<Page<GRNResponse>> getAllGRNs(Pageable pageable) { // @ParameterObject helps with Swagger documentation
        log.info("Fetching all GRNs");
        return ResponseEntity.ok(grnService.getAllGRNs(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GRNResponse> getGRNById(@PathVariable Long id) {
        log.info("Fetching GRN with ID: {}", id);
        return ResponseEntity.ok(grnService.getGRNById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GRNResponse> updateGRN(
            @PathVariable Long id, 
            @Valid @RequestBody GRNUpdateRequest request) {
        log.info("Updating GRN ID: {} with data: {}", id, request);
        return ResponseEntity.ok(grnService.updateGRN(id, request));
    }

    // @DeleteMapping("/{id}")
    // public ResponseEntity<Void> deleteGRN(@PathVariable Long id) {
    //     log.warn("Deleting GRN with ID: {}", id);
    //     grnService.cancelGRN(id);
    //     return ResponseEntity.noContent().build();
    // }

    @PatchMapping("/{id}/cancel")  // ✅ PATCH for soft delete / status change
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