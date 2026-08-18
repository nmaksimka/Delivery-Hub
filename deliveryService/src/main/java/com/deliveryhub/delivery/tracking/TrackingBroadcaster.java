package com.deliveryhub.delivery.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TrackingBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TrackingBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;

    public TrackingBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void broadcast(TrackingUpdate update) {
        try {
            messagingTemplate.convertAndSend(TrackingWebSocketConfig.topicOf(update.orderId()), update);
        } catch (MessagingException e) {
            log.warn("Tracking update of order {} was not sent: {}", update.orderId(), e.getMessage());
        }
    }
}
