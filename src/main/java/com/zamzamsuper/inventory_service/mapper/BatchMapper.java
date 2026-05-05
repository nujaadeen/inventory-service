package com.zamzamsuper.inventory_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.zamzamsuper.inventory_service.dto.BatchRequest;
import com.zamzamsuper.inventory_service.dto.BatchResponse;
import com.zamzamsuper.inventory_service.model.Batch;

@Mapper(componentModel = "spring", uses = {ProductPriceMapper.class})
public interface BatchMapper {

    @Mapping(source = "stock.id", target = "stockId")
    @Mapping(source = "grn.id", target = "grnId")
    BatchResponse toResponse(Batch entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "grn", ignore = true)   // Set manually in Service
    @Mapping(target = "stock", ignore = true) // Set manually in Service
    @Mapping(target = "prices", ignore = true) // Handled by loop logic in Service
    @Mapping(target = "createdAt", ignore = true) 
    @Mapping(target = "updatedAt", ignore = true) 
    @Mapping(target = "status", defaultValue = "ACTIVE")
    Batch toEntity(BatchRequest request);
}