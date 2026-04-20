package com.deliveryhub.order.mapper;

import com.deliveryhub.order.dto.OrderItemDto;
import com.deliveryhub.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {
    OrderItemDto toDto(OrderItem orderItem);

    @Mapping(target = "order", ignore = true)
    OrderItem toEntity(OrderItemDto orderItemDto);

    @Mapping(target = "order", ignore = true)
    void updateEntityFromDto(OrderItemDto orderItemDto,@MappingTarget OrderItem orderItem);
}
