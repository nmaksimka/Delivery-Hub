package com.deliveryhub.restaurant.config;

import com.deliveryhub.contracts.Topics;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(KafkaTopicsConfig.TopicProperties.class)
public class KafkaTopicsConfig {

    @Bean
    public KafkaAdmin.NewTopics restaurantTopics(TopicProperties properties) {
        return new KafkaAdmin.NewTopics(TopicBuilder.name(Topics.deadLetterOf(Topics.REVIEW_SUBMITTED))
                .partitions(properties.partitions())
                .replicas(properties.replicas())
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(properties.deadLetterRetention().toMillis()))
                .build());
    }

    @ConfigurationProperties("app.kafka.topics")
    public record TopicProperties(
            @DefaultValue("3") int partitions,
            @DefaultValue("1") short replicas,
            @DefaultValue("30d") Duration deadLetterRetention) {
    }
}
