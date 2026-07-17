package com.deliveryhub.restaurant.mapper;

import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.entity.Restaurant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ImageLinks.class)
public interface RestaurantMapper {
    @Mapping(source = "restaurant", target = "imageUrl", qualifiedByName = "restaurantImage")
    RestaurantDto toDto(Restaurant restaurant);
}
