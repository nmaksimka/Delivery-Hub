package com.deliveryhub.delivery.security;

public record CurrentUser(Long id, String role) {

    public boolean seesEveryDelivery() {
        return "ADMIN".equals(role) || "COURIER".equals(role);
    }
}
