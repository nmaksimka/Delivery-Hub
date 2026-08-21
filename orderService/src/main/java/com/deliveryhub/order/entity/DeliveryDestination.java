package com.deliveryhub.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.util.Objects;

@Embeddable
public record DeliveryDestination(
        @Column(name = "delivery_address_id") Long addressId,
        @Column(name = "delivery_address", length = 400) String line,
        @Column(name = "delivery_comment", length = 300) String comment,
        @Column(name = "delivery_latitude", precision = 9, scale = 6) BigDecimal latitude,
        @Column(name = "delivery_longitude", precision = 9, scale = 6) BigDecimal longitude) {

    public DeliveryDestination {
        Objects.requireNonNull(addressId, "addressId");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(latitude, "latitude");
        Objects.requireNonNull(longitude, "longitude");
    }
}
