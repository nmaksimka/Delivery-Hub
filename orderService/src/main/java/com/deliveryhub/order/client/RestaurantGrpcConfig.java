package com.deliveryhub.order.client;

import com.deliveryhub.contracts.pricing.MenuPricingGrpc;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RestaurantGrpcProperties.class)
public class RestaurantGrpcConfig {

    static final String CHANNEL_NAME = "restaurant";

    @Bean
    MenuPricingGrpc.MenuPricingBlockingStub menuPricingStub(GrpcChannelFactory channels) {
        return MenuPricingGrpc.newBlockingStub(channels.createChannel(CHANNEL_NAME));
    }
}
