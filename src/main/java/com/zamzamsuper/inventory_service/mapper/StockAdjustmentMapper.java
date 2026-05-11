package com.zamzamsuper.inventory_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.zamzamsuper.inventory_service.dto.StockAdjustmentRequest;
import com.zamzamsuper.inventory_service.dto.StockAdjustmentResponse;
import com.zamzamsuper.inventory_service.model.StockAdjustment;

@Mapper(componentModel = "spring")
public interface StockAdjustmentMapper {
    @Mapping(source = "batch.id", target = "batchId")
    @Mapping(source = "batch.batchNumber", target = "batchNumber")
    StockAdjustmentResponse toResponse(StockAdjustment entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "batch", ignore = true) // Set manually in Service
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    StockAdjustment toEntity(StockAdjustmentRequest request);
}
