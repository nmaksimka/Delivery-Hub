package com.deliveryhub.order.security;

public record CurrentUser(Long id, String role) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
