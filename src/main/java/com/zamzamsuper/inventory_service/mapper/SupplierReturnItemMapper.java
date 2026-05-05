package com.zamzamsuper.inventory_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.zamzamsuper.inventory_service.dto.SupplierReturnItemRequest;
import com.zamzamsuper.inventory_service.dto.SupplierReturnItemResponse;
import com.zamzamsuper.inventory_service.model.SupplierReturnItem;

@Mapper(componentModel = "spring")
public interface SupplierReturnItemMapper {
    @Mapping(source = "supplierReturn.id", target = "supplierReturnId")
    @Mapping(source = "batch.id", target = "batchId")
    @Mapping(source = "batch.batchNumber", target = "batchNumber")
    SupplierReturnItemResponse toResponse(SupplierReturnItem entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "supplierReturn", ignore = true) // Set manually in Service
    @Mapping(target = "batch", ignore = true) // Set manually in Service
    @Mapping(target = "createdAt", ignore = true) 
    @Mapping(target = "updatedAt", ignore = true) 
    SupplierReturnItem toEntity(SupplierReturnItemRequest request);
}
