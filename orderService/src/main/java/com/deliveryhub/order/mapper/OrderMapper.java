package com.deliveryhub.order.mapper;

import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.dto.OrderItemDto;
import com.deliveryhub.order.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {
    OrderDto toDto(Order order);
    Order toEntity(OrderDto orderDto);
    void updateEntityFromDto(OrderDto orderDto,@MappingTarget Order order);
}
