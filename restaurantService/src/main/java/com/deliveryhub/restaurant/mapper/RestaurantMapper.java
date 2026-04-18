package com.deliveryhub.restaurant.mapper;


import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.entity.Restaurant;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface RestaurantMapper {
    RestaurantDto toDto(Restaurant restaurant);
    Restaurant toEntity(RestaurantDto restaurantDto);
    void updateEntityFromDto(RestaurantDto restaurantDto, @MappingTarget Restaurant restaurant);
}
