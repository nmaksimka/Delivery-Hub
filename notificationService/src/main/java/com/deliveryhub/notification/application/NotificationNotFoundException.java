package com.deliveryhub.notification.application;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(UUID id) {
        super("Notification " + id + " was not found");
    }
}
