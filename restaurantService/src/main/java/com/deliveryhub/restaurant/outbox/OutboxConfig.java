package com.deliveryhub.restaurant.outbox;

import com.deliveryhub.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {

    @Bean
    public NewTopic restaurantCatalogChangedTopic() {
        return TopicBuilder.name(Topics.RESTAURANT_CATALOG_CHANGED).partitions(3).replicas(1).build();
    }
}
