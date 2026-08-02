package com.deliveryhub.order.idempotency;

public record IdempotencyScope(Long userId, String method, String path, String key) {
}
