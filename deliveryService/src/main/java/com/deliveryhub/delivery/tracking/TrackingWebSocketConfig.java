package com.deliveryhub.delivery.tracking;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class TrackingWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/ws/tracking";
    public static final String TOPIC_PREFIX = "/topic";

    private static final long HEARTBEAT_MILLIS = 10_000;

    private final GatewayIdentityHandshake gatewayIdentityHandshake;
    private final SubscriptionGuard subscriptionGuard;
    private final String[] allowedOriginPatterns;
    private TaskScheduler heartbeatScheduler;

    public TrackingWebSocketConfig(GatewayIdentityHandshake gatewayIdentityHandshake,
                                   SubscriptionGuard subscriptionGuard,
                                   @Value("${app.tracking.allowed-origin-patterns}") String[] allowedOriginPatterns) {
        this.gatewayIdentityHandshake = gatewayIdentityHandshake;
        this.subscriptionGuard = subscriptionGuard;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Autowired
    public void setHeartbeatScheduler(@Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler heartbeatScheduler) {
        this.heartbeatScheduler = heartbeatScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .addInterceptors(gatewayIdentityHandshake);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(TOPIC_PREFIX)
                .setHeartbeatValue(new long[]{HEARTBEAT_MILLIS, HEARTBEAT_MILLIS})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscriptionGuard);
    }

    public static String topicOf(long orderId) {
        return TOPIC_PREFIX + "/orders/" + orderId + "/tracking";
    }
}
