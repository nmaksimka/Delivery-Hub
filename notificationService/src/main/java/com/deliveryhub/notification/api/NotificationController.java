package com.deliveryhub.notification.api;

import com.deliveryhub.notification.application.NotificationHistory;
import com.deliveryhub.notification.domain.Notification;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final String USER_HEADER = "X-User-Id";

    private final NotificationHistory history;

    public NotificationController(NotificationHistory history) {
        this.history = history;
    }

    @GetMapping
    public NotificationPageDto list(@RequestHeader(USER_HEADER) long userId,
                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<Notification> found = history.pageOf(userId, page, size);
        return new NotificationPageDto(found.map(NotificationDto::of).getContent(), found.getNumber(),
                found.getSize(), found.getTotalElements(), found.getTotalPages(), history.unreadCountOf(userId));
    }

    @PostMapping("/{id}/read")
    public NotificationDto markRead(@RequestHeader(USER_HEADER) long userId, @PathVariable UUID id) {
        return NotificationDto.of(history.markRead(userId, id));
    }
}
