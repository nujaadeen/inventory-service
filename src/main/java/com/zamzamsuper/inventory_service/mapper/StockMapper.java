package com.zamzamsuper.inventory_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.zamzamsuper.inventory_service.dto.StockRequest;
import com.zamzamsuper.inventory_service.dto.StockResponse;
import com.zamzamsuper.inventory_service.model.Stock;

@Mapper(componentModel = "spring")
public interface StockMapper {

    @Mapping(source = "location.id", target = "locationId")
    @Mapping(source = "location.name", target = "locationName")
    StockResponse toResponse(Stock entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "location", ignore = true) // Set manually in Service
    @Mapping(target = "createdAt", ignore = true) 
    @Mapping(target = "updatedAt", ignore = true) 
    Stock toEntity(StockRequest request);
}