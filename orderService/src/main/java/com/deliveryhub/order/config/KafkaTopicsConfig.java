package com.deliveryhub.order.config;

import com.deliveryhub.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.time.Duration;
import java.util.stream.Stream;

@Configuration
@EnableConfigurationProperties(TopicProperties.class)
public class KafkaTopicsConfig {

    @Bean
    public KafkaAdmin.NewTopics orderTopics(TopicProperties properties) {
        Stream<NewTopic> published = Stream.of(Topics.ORDER_CREATED, Topics.ORDER_CANCELLED, Topics.REVIEW_SUBMITTED)
                .map(name -> topic(name, properties, properties.retention()));
        Stream<NewTopic> deadLetters = Stream.of(Topics.DELIVERY_STATUS_UPDATED, Topics.COURIER_ASSIGNED,
                        Topics.PAYMENT_HELD, Topics.PAYMENT_REJECTED, Topics.PAYMENT_CAPTURED,
                        Topics.PAYMENT_RELEASED, Topics.PAYMENT_REFUNDED)
                .map(name -> topic(Topics.deadLetterOf(name), properties, properties.deadLetterRetention()));
        return new KafkaAdmin.NewTopics(Stream.concat(published, deadLetters).toArray(NewTopic[]::new));
    }

    private static NewTopic topic(String name, TopicProperties properties, Duration retention) {
        return TopicBuilder.name(name)
                .partitions(properties.partitions())
                .replicas(properties.replicas())
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(retention.toMillis()))
                .build();
    }
}
