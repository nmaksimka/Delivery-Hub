package com.deliveryhub.delivery.mapper;

import com.deliveryhub.delivery.dto.DeliveryDto;
import com.deliveryhub.delivery.entity.Delivery;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DeliveryMapper {
    DeliveryDto toDto(Delivery delivery);
}
