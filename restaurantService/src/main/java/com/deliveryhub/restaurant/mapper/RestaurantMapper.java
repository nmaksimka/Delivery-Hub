package com.deliveryhub.restaurant.mapper;

import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.entity.Restaurant;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RestaurantMapper {
    RestaurantDto toDto(Restaurant restaurant);
}
