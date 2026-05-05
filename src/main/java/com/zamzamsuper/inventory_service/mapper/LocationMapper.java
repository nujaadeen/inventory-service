package com.zamzamsuper.inventory_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.zamzamsuper.inventory_service.dto.LocationRequest;
import com.zamzamsuper.inventory_service.dto.LocationResponse;
import com.zamzamsuper.inventory_service.model.Location;

@Mapper(componentModel = "spring")
public interface LocationMapper {
    LocationResponse toResponse(Location entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true) 
    @Mapping(target = "updatedAt", ignore = true) 
    Location toEntity(LocationRequest request);
}
