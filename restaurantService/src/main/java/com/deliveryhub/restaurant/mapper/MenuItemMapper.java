package com.deliveryhub.restaurant.mapper;

import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.dto.MenuItemView;
import com.deliveryhub.restaurant.entity.MenuItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ImageLinks.class)
public interface MenuItemMapper {
    @Mapping(source = "restaurant.id", target = "restaurantId")
    @Mapping(source = "menuItem", target = "imageUrl", qualifiedByName = "menuItemImage")
    MenuItemDto toDto(MenuItem menuItem);

    @Mapping(source = "restaurant.id", target = "restaurantId")
    @Mapping(source = "imageUrl", target = "externalImageUrl")
    MenuItemView toView(MenuItem menuItem);

    @Mapping(source = "view", target = "imageUrl", qualifiedByName = "menuItemViewImage")
    MenuItemDto fromView(MenuItemView view);
}
