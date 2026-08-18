package com.deliveryhub.delivery.tracking;

import com.deliveryhub.delivery.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SubscriptionGuard implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionGuard.class);
    private static final Pattern ORDER_TOPIC = Pattern.compile("^/topic/orders/(\\d{1,18})/tracking$");

    private final TrackingAccess trackingAccess;

    public SubscriptionGuard(TrackingAccess trackingAccess) {
        this.trackingAccess = trackingAccess;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.SEND) {
            throw new MessageDeliveryException(message, "Tracking is read-only, sending is not allowed");
        }
        if (command == StompCommand.CONNECT) {
            userOf(accessor, message);
        }
        if (command == StompCommand.SUBSCRIBE) {
            checkSubscription(userOf(accessor, message), accessor.getDestination(), message);
        }
        return message;
    }

    private void checkSubscription(CurrentUser user, String destination, Message<?> message) {
        Matcher matcher = destination == null ? null : ORDER_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            throw new MessageDeliveryException(message, "Only /topic/orders/{orderId}/tracking can be subscribed");
        }
        long orderId = Long.parseLong(matcher.group(1));
        if (!trackingAccess.canWatch(user, orderId)) {
            log.info("User {} with role {} may not watch order {}", user.id(), user.role(), orderId);
            throw new MessageDeliveryException(message, "Order " + orderId + " is not available to you");
        }
    }

    private static CurrentUser userOf(StompHeaderAccessor accessor, Message<?> message) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object user = attributes == null ? null : attributes.get(GatewayIdentityHandshake.CURRENT_USER);
        if (user instanceof CurrentUser currentUser) {
            return currentUser;
        }
        throw new MessageDeliveryException(message, "Tracking socket has no authenticated user");
    }
}
