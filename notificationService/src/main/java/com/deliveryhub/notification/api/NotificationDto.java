package com.deliveryhub.notification.api;

import com.deliveryhub.notification.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(UUID id, String type, Long orderId, String title, String message, boolean read,
                              Instant createdAt) {

    public static NotificationDto of(Notification notification) {
        return new NotificationDto(notification.getId(), notification.getType().name(), notification.getOrderId(),
                notification.getTitle(), notification.getMessage(), notification.isRead(),
                notification.getCreatedAt());
    }
}
