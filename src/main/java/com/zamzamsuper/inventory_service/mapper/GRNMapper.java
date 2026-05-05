package com.zamzamsuper.inventory_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.zamzamsuper.inventory_service.dto.grn.GRNCreateRequest;
import com.zamzamsuper.inventory_service.dto.grn.GRNResponse;
import com.zamzamsuper.inventory_service.model.GRN;

@Mapper(componentModel = "spring", uses = {BatchMapper.class})
public interface GRNMapper {

    @Mapping(source = "supplier.id", target = "supplierId")
    @Mapping(source = "supplier.name", target = "supplierName")
    GRNResponse toResponse(GRN entity);

    @Mapping(target = "id", ignore = true) 
    @Mapping(target = "batches", ignore = true) 
    @Mapping(target = "supplier", ignore = true) 
    @Mapping(target = "createdAt", ignore = true) 
    @Mapping(target = "updatedAt", ignore = true) 
    @Mapping(target = "status", defaultValue = "POSTED")
    GRN toEntity(GRNCreateRequest request);
}
