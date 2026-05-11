package com.zamzamsuper.inventory_service.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.zamzamsuper.inventory_service.dto.StockAdjustmentRequest;
import com.zamzamsuper.inventory_service.dto.StockAdjustmentResponse;
import com.zamzamsuper.inventory_service.mapper.StockAdjustmentMapper;
import com.zamzamsuper.inventory_service.model.Batch;
import com.zamzamsuper.inventory_service.model.StockAdjustment;
import com.zamzamsuper.inventory_service.repository.BatchRepository;
import com.zamzamsuper.inventory_service.repository.StockAdjustmentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockAdjustmentService {

    private final StockAdjustmentMapper adjustmentMapper;
    private final StockAdjustmentRepository adjustmentRepository;
    private final BatchRepository batchRepository;

    public StockAdjustmentResponse createAdjustment(StockAdjustmentRequest request) {
        Batch batch =
                batchRepository
                        .findById(request.batchId())
                        .orElseThrow(() -> new RuntimeException("Batch not found"));

        StockAdjustment adjustment = adjustmentMapper.toEntity(request);
        adjustment.setBatch(batch);
        StockAdjustment savedAdjustment = adjustmentRepository.save(adjustment);
        return adjustmentMapper.toResponse(savedAdjustment);
    }

    public List<StockAdjustmentResponse> getAllAdjustments() {
        return adjustmentRepository.findAll().stream()
                .map(adjustmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    public StockAdjustmentResponse getAdjustmentById(Long id) {
        StockAdjustment adjustment =
                adjustmentRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new RuntimeException("Adjustment not found with id " + id));
        return adjustmentMapper.toResponse(adjustment);
    }

    public StockAdjustmentResponse updateAdjustment(Long id, StockAdjustmentRequest request) {
        StockAdjustment existing =
                adjustmentRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new RuntimeException("Adjustment not found with id " + id));

        Batch batch =
                batchRepository
                        .findById(request.batchId())
                        .orElseThrow(() -> new RuntimeException("Batch not found"));

        existing.setBatch(batch);
        existing.setStaffId(request.staffId());
        existing.setAdjustmentType(request.adjustmentType());
        existing.setQuantity(request.quantity());
        existing.setReason(request.reason());

        StockAdjustment updated = adjustmentRepository.save(existing);
        return adjustmentMapper.toResponse(updated);
    }

    public void deleteAdjustment(Long id) {
        if (!adjustmentRepository.existsById(id)) {
            throw new RuntimeException("Adjustment not found with id " + id);
        }
        adjustmentRepository.deleteById(id);
    }
}
