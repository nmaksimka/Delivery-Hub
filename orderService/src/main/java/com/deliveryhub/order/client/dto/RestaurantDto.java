package com.deliveryhub.order.client.dto;

public record RestaurantDto(Long id, String name, boolean active, boolean closed, String workingHours,
                            GeoLocation location) {

    public RestaurantDto(Long id, String name, boolean active) {
        this(id, name, active, false, "", null);
    }
}
