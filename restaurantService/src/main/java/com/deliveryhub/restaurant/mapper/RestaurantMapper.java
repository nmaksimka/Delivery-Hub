package com.deliveryhub.restaurant.mapper;


import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.entity.Restaurant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface RestaurantMapper {
    RestaurantDto toDto(Restaurant restaurant);

    @Mapping(target = "id", ignore = true)
    Restaurant toEntity(RestaurantDto restaurantDto);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(RestaurantDto restaurantDto, @MappingTarget Restaurant restaurant);
}
