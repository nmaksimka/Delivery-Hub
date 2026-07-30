package com.deliveryhub.restaurant.mapper;

import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.dto.RestaurantView;
import com.deliveryhub.restaurant.entity.Restaurant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ImageLinks.class)
public interface RestaurantMapper {
    @Mapping(source = "restaurant", target = "imageUrl", qualifiedByName = "restaurantImage")
    RestaurantDto toDto(Restaurant restaurant);

    @Mapping(source = "imageUrl", target = "externalImageUrl")
    RestaurantView toView(Restaurant restaurant);

    @Mapping(source = "view", target = "imageUrl", qualifiedByName = "restaurantViewImage")
    RestaurantDto fromView(RestaurantView view);
}
