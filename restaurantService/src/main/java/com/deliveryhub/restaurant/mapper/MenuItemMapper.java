package com.deliveryhub.restaurant.mapper;

import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.entity.MenuItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ImageLinks.class)
public interface MenuItemMapper {
    @Mapping(source = "restaurant.id", target = "restaurantId")
    @Mapping(source = "menuItem", target = "imageUrl", qualifiedByName = "menuItemImage")
    MenuItemDto toDto(MenuItem menuItem);
}
