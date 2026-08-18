package com.deliveryhub.delivery.config;

import com.deliveryhub.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(TopicProperties.class)
public class KafkaTopicsConfig {

    @Bean
    public KafkaAdmin.NewTopics deliveryTopics(TopicProperties properties) {
        return new KafkaAdmin.NewTopics(
                topic(Topics.DELIVERY_STATUS_UPDATED, properties, properties.retention()),
                topic(Topics.COURIER_ASSIGNED, properties, properties.retention()),
                topic(Topics.DELIVERY_REJECTED, properties, properties.retention()),
                topic(Topics.deadLetterOf(Topics.PAYMENT_HELD), properties, properties.deadLetterRetention()),
                topic(Topics.deadLetterOf(Topics.ORDER_CANCELLED), properties, properties.deadLetterRetention()),
                topic(Topics.deadLetterOf(Topics.PAYMENT_CAPTURED), properties, properties.deadLetterRetention()));
    }

    private static NewTopic topic(String name, TopicProperties properties, Duration retention) {
        return TopicBuilder.name(name)
                .partitions(properties.partitions())
                .replicas(properties.replicas())
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(retention.toMillis()))
                .build();
    }
}
