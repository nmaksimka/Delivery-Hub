package com.deliveryhub.delivery.tracking;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.entity.GeoPosition;

import java.time.Instant;

public record TrackingUpdate(
        Type type,
        long orderId,
        long deliveryId,
        DeliveryStatus status,
        CourierPosition courier,
        Long remainingMeters,
        Long etaSeconds,
        Instant estimatedArrival,
        Instant at) {

    public enum Type {
        POSITION,
        STATUS
    }

    public record CourierPosition(double latitude, double longitude, double heading, Instant ts) {
    }

    public static TrackingUpdate status(Delivery delivery) {
        return new TrackingUpdate(Type.STATUS, delivery.getOrderId(), delivery.getId(), delivery.getStatus(),
                null, null, null, null, delivery.getUpdatedAt());
    }

    public static TrackingUpdate position(Delivery delivery, GeoPosition position, double heading, Eta eta,
                                          Instant at) {
        CourierPosition courier = new CourierPosition(position.latitude(), position.longitude(), heading, at);
        if (eta == null) {
            return new TrackingUpdate(Type.POSITION, delivery.getOrderId(), delivery.getId(), delivery.getStatus(),
                    courier, null, null, null, at);
        }
        return new TrackingUpdate(Type.POSITION, delivery.getOrderId(), delivery.getId(), delivery.getStatus(),
                courier, eta.remainingMeters(), eta.seconds(), at.plusSeconds(eta.seconds()), at);
    }

    public boolean finishesDelivery() {
        return status == DeliveryStatus.DELIVERED || status == DeliveryStatus.CANCELLED;
    }
}
