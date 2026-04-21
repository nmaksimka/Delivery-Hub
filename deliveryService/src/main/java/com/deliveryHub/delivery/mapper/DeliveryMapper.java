package com.deliveryHub.delivery.mapper;

import com.deliveryHub.delivery.dto.DeliveryDto;
import com.deliveryHub.delivery.entity.Delivery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface DeliveryMapper {
    DeliveryDto toDto(Delivery delivery);

    @Mapping(target = "id", ignore = true)
    Delivery toEntity(DeliveryDto deliveryDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderId", ignore = true)
    void updateEntityFromDto(DeliveryDto deliveryDto, @MappingTarget Delivery delivery);
}
