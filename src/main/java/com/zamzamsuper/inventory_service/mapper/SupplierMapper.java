package com.zamzamsuper.inventory_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.zamzamsuper.inventory_service.dto.SupplierRequest;
import com.zamzamsuper.inventory_service.dto.SupplierResponse;
import com.zamzamsuper.inventory_service.model.Supplier;

@Mapper(componentModel = "spring")
public interface SupplierMapper {
    SupplierResponse toResponse(Supplier entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Supplier toEntity(SupplierRequest request);
}
