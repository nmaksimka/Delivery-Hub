package com.deliveryhub.order.mapper;

import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.entity.Order;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {
    OrderDto toDto(Order order);
}
