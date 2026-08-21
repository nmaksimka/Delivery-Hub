package com.deliveryhub.delivery.security;

public record CurrentUser(Long id, String role) {

    public boolean seesEveryDelivery() {
        return isAdmin() || isCourier();
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isCourier() {
        return "COURIER".equals(role);
    }
}
