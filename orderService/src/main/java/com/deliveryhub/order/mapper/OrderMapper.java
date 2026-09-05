package com.deliveryhub.order.mapper;

import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {

    OrderDto toDto(Order order);

    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "items", source = "orderItems")
    OrderCreatedEvent toOrderCreatedEvent(Order order);

    OrderCreatedEvent.OrderItemPayload toItemPayload(OrderItem orderItem);
}
