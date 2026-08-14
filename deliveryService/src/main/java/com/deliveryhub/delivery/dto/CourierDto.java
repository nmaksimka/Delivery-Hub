package com.deliveryhub.delivery.dto;

import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.CourierStatus;
import com.deliveryhub.delivery.entity.GeoPosition;

import java.time.Instant;

public record CourierDto(
        Long id,
        Long userId,
        String name,
        String phone,
        CourierStatus status,
        Double latitude,
        Double longitude,
        Instant updatedAt) {

    public static CourierDto of(Courier courier) {
        GeoPosition position = courier.getPosition();
        return new CourierDto(courier.getId(), courier.getUserId(), courier.getName(), courier.getPhone(),
                courier.getStatus(),
                position == null ? null : position.latitude(),
                position == null ? null : position.longitude(),
                courier.getUpdatedAt());
    }
}
