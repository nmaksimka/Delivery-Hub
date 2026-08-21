package com.deliveryhub.delivery.dispatch;

import com.deliveryhub.delivery.entity.GeoPosition;

public record DeliveryZone(GeoPosition center, int maxDistanceMeters) {
}
