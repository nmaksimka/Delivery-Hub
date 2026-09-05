package com.deliveryhub.order.mapper;

import com.deliveryhub.order.dto.OrderItemDto;
import com.deliveryhub.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    @Mapping(target = "menuItemName", source = "itemName")
    OrderItemDto toDto(OrderItem orderItem);
}
