package com.zamzamsuper.inventory_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.zamzamsuper.inventory_service.dto.SupplierReturnRequest;
import com.zamzamsuper.inventory_service.dto.SupplierReturnResponse;
import com.zamzamsuper.inventory_service.model.SupplierReturn;

@Mapper(componentModel = "spring")
public interface SupplierReturnMapper {
    @Mapping(source = "supplier.id", target = "supplierId")
    @Mapping(source = "supplier.name", target = "supplierName")
    @Mapping(source = "originalGrn.id", target = "originalGrnId")
    SupplierReturnResponse toResponse(SupplierReturn entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "supplier", ignore = true) // Set manually in Service
    @Mapping(target = "originalGrn", ignore = true) // Set manually in Service
    @Mapping(target = "createdAt", ignore = true) 
    @Mapping(target = "updatedAt", ignore = true) 
    SupplierReturn toEntity(SupplierReturnRequest request);

}
