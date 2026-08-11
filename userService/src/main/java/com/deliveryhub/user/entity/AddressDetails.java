package com.deliveryhub.user.entity;

public record AddressDetails(
        String label,
        String city,
        String street,
        String house,
        String apartment,
        String entrance,
        String floor,
        String courierComment) {
}
