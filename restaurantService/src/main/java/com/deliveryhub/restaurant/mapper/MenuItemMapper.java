package com.deliveryhub.restaurant.mapper;

import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.entity.MenuItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface MenuItemMapper {
    @Mapping(source = "restaurant.id", target = "restaurantId")
    MenuItemDto toDto(MenuItem menuItem);

    @Mapping(target = "restaurant", ignore = true)
    MenuItem toEntity(MenuItemDto menuItemDto);

    @Mapping(target = "restaurant", ignore = true)
    void updateEntityFromDto(MenuItemDto dto, @MappingTarget MenuItem menuItem);
}
