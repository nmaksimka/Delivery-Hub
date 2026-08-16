package com.deliveryhub.delivery.quote;

public class OutOfDeliveryZoneException extends RuntimeException {

    public OutOfDeliveryZoneException(long restaurantId, int distanceMeters, int maxDistanceMeters) {
        super("Address is " + distanceMeters + " m away from restaurant " + restaurantId
                + ", the delivery zone is " + maxDistanceMeters + " m");
    }
}
