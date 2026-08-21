package com.deliveryhub.notification.application;

import com.deliveryhub.notification.domain.Notification;
import com.deliveryhub.notification.domain.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class NotificationHistory {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationHistory(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    public Page<Notification> pageOf(long userId, int page, int size) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    public long unreadCountOf(long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public Notification markRead(long userId, UUID notificationId) {
        notifications.markRead(notificationId, userId, clock.instant());
        return notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }
}
