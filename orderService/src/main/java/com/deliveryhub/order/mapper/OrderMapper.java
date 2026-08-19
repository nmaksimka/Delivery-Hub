package com.deliveryhub.order.mapper;

import com.deliveryhub.order.dto.OrderDto;
import com.deliveryhub.order.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {

    @Mapping(target = "deliveryAddress", source = "destination.line")
    @Mapping(target = "deliveryLatitude", source = "destination.latitude")
    @Mapping(target = "deliveryLongitude", source = "destination.longitude")
    @Mapping(target = "weatherSurcharge", source = "feeBreakdown.weatherSurcharge")
    @Mapping(target = "deliveryDistanceMeters", source = "feeBreakdown.distanceMeters")
    @Mapping(target = "routeSource", source = "feeBreakdown.routeSource")
    OrderDto toDto(Order order);
}
