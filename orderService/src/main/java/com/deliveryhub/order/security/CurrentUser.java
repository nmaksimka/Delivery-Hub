package com.deliveryhub.order.security;

public record CurrentUser(Long id, String role) {

    public static final String ADMIN = "ADMIN";

    public static boolean isAdmin(String role) {
        return ADMIN.equals(role);
    }

    public boolean isAdmin() {
        return isAdmin(role);
    }
}
