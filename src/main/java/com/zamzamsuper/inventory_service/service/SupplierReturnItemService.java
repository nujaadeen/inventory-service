package com.zamzamsuper.inventory_service.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.zamzamsuper.inventory_service.dto.SupplierReturnItemRequest;
import com.zamzamsuper.inventory_service.dto.SupplierReturnItemResponse;
import com.zamzamsuper.inventory_service.mapper.SupplierReturnItemMapper;
import com.zamzamsuper.inventory_service.model.Batch;
import com.zamzamsuper.inventory_service.model.SupplierReturn;
import com.zamzamsuper.inventory_service.model.SupplierReturnItem;
import com.zamzamsuper.inventory_service.repository.BatchRepository;
import com.zamzamsuper.inventory_service.repository.SupplierReturnItemRepository;
import com.zamzamsuper.inventory_service.repository.SupplierReturnRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SupplierReturnItemService {

    private final SupplierReturnItemMapper returnItemMapper;
    private final SupplierReturnItemRepository returnItemRepository;
    private final SupplierReturnRepository returnRepository;
    private final BatchRepository batchRepository;

    public SupplierReturnItemResponse createItem(SupplierReturnItemRequest request) {
        SupplierReturn supplierReturn = returnRepository.findById(request.supplierReturnId())
                .orElseThrow(() -> new RuntimeException("Supplier Return not found"));

        Batch batch = batchRepository.findById(request.batchId())
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        SupplierReturnItem item = returnItemMapper.toEntity(request);
        item.setSupplierReturn(supplierReturn);
        item.setBatch(batch);
        SupplierReturnItem savedItem = returnItemRepository.save(item);
        return returnItemMapper.toResponse(savedItem);
    }

    public List<SupplierReturnItemResponse> getAllItems() {
        return returnItemRepository.findAll()
                .stream()
                .map(returnItemMapper::toResponse)
                .collect(Collectors.toList());
    }

    public SupplierReturnItemResponse getItemById(Long id) {
        SupplierReturnItem item = returnItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Return item not found with id " + id));
        return returnItemMapper.toResponse(item);
    }

    public SupplierReturnItemResponse updateItem(Long id, SupplierReturnItemRequest request) {
        SupplierReturnItem existing = returnItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Return item not found with id " + id));

        SupplierReturn supplierReturn = returnRepository.findById(request.supplierReturnId())
                .orElseThrow(() -> new RuntimeException("Supplier Return not found"));
        
        Batch batch = batchRepository.findById(request.batchId())
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        existing.setSupplierReturn(supplierReturn);
        existing.setBatch(batch);
        existing.setQtyReturned(request.qtyReturned());
        existing.setUnitCostRefunded(request.unitCostRefunded());
        existing.setReason(request.reason());

        SupplierReturnItem updated = returnItemRepository.save(existing);
        return returnItemMapper.toResponse(updated);
    }

    public void deleteItem(Long id) {
        if (!returnItemRepository.existsById(id)) {
            throw new RuntimeException("Return item not found with id " + id);
        }
        returnItemRepository.deleteById(id);
    }
}