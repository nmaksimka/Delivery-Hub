package com.deliveryhub.delivery.tracking;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class TrackingWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/ws/tracking";
    public static final String TOPIC_PREFIX = "/topic";

    private final GatewayIdentityHandshake gatewayIdentityHandshake;
    private final SubscriptionGuard subscriptionGuard;

    public TrackingWebSocketConfig(GatewayIdentityHandshake gatewayIdentityHandshake,
                                   SubscriptionGuard subscriptionGuard) {
        this.gatewayIdentityHandshake = gatewayIdentityHandshake;
        this.subscriptionGuard = subscriptionGuard;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOriginPatterns("*")
                .addInterceptors(gatewayIdentityHandshake);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(TOPIC_PREFIX);
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
