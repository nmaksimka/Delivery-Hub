package com.deliveryhub.order.mapper;

import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {

    @Mapping(target = "deliveryAddress", source = "destination.line")
    OrderDto toDto(Order order);
}
