package com.zamzamsuper.inventory_service.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.zamzamsuper.inventory_service.dto.SupplierReturnRequest;
import com.zamzamsuper.inventory_service.dto.SupplierReturnResponse;
import com.zamzamsuper.inventory_service.mapper.SupplierReturnMapper;
import com.zamzamsuper.inventory_service.model.GRN;
import com.zamzamsuper.inventory_service.model.Supplier;
import com.zamzamsuper.inventory_service.model.SupplierReturn;
import com.zamzamsuper.inventory_service.repository.GRNRepository;
import com.zamzamsuper.inventory_service.repository.SupplierRepository;
import com.zamzamsuper.inventory_service.repository.SupplierReturnRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupplierReturnService {

    private final SupplierReturnMapper returnMapper;
    private final SupplierReturnRepository returnRepository;
    private final SupplierRepository supplierRepository;
    private final GRNRepository grnRepository;

    public SupplierReturnResponse createReturn(SupplierReturnRequest request) {
        Supplier supplier =
                supplierRepository
                        .findById(request.supplierId())
                        .orElseThrow(() -> new RuntimeException("Supplier not found"));

        GRN grn =
                (request.originalGrnId() != null)
                        ? grnRepository.findById(request.originalGrnId()).orElse(null)
                        : null;

        SupplierReturn supplierReturn = returnMapper.toEntity(request);
        supplierReturn.setSupplier(supplier);
        supplierReturn.setOriginalGrn(grn);
        SupplierReturn savedSupplierReturn = returnRepository.save(supplierReturn);
        return returnMapper.toResponse(savedSupplierReturn);
    }

    public List<SupplierReturnResponse> getAllReturns() {
        return returnRepository.findAll().stream()
                .map(returnMapper::toResponse)
                .collect(Collectors.toList());
    }

    public SupplierReturnResponse getReturnById(Long id) {
        SupplierReturn supplierReturn =
                returnRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Supplier Return not found with id " + id));
        return returnMapper.toResponse(supplierReturn);
    }

    public SupplierReturnResponse updateReturn(Long id, SupplierReturnRequest request) {
        SupplierReturn existing =
                returnRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Supplier Return not found with id " + id));

        Supplier supplier =
                supplierRepository
                        .findById(request.supplierId())
                        .orElseThrow(() -> new RuntimeException("Supplier not found"));

        // GRN is optional in your model, so we handle nulls safely
        GRN grn =
                (request.originalGrnId() != null)
                        ? grnRepository.findById(request.originalGrnId()).orElse(null)
                        : null;

        existing.setSupplier(supplier);
        existing.setOriginalGrn(grn);
        existing.setReturnDate(request.returnDate());
        existing.setTotalRefundValue(request.totalRefundValue());
        existing.setReturnStatus(request.returnStatus());

        SupplierReturn updated = returnRepository.save(existing);
        return returnMapper.toResponse(updated);
    }

    public void deleteReturn(Long id) {
        if (!returnRepository.existsById(id)) {
            throw new RuntimeException("Supplier Return not found with id " + id);
        }
        returnRepository.deleteById(id);
    }
}
