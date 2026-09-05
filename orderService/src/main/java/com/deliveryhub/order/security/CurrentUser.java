package com.deliveryhub.order.security;

/**
 * Личность вызывающего, переданная API Gateway после проверки JWT.
 * Сервис не разбирает токен сам, но и не доверяет телу запроса.
 */
public record CurrentUser(Long id, String role) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
