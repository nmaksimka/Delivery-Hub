package com.deliveryhub.payment.application;

public record CurrentUser(long id, String role) {

    public static final String ADMIN = "ADMIN";

    public boolean isAdmin() {
        return ADMIN.equals(role);
    }
}
