package com.zamzamsuper.inventory_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.zamzamsuper.inventory_service.dto.ProductPriceRequest;
import com.zamzamsuper.inventory_service.dto.ProductPriceResponse;
import com.zamzamsuper.inventory_service.model.ProductPrice;

@Mapper(componentModel = "spring")
public interface ProductPriceMapper {

    @Mapping(source = "batch.id", target = "batchId")
    @Mapping(source = "batch.batchNumber", target = "batchNumber")
    ProductPriceResponse toResponse(ProductPrice entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "batch", ignore = true) // Set manually in Service
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", defaultValue = "true") // Null handling
    ProductPrice toEntity(ProductPriceRequest request);
}
