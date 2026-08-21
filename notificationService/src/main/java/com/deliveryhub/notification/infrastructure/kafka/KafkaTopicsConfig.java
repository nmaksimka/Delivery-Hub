package com.deliveryhub.notification.infrastructure.kafka;

import com.deliveryhub.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
@EnableConfigurationProperties(TopicProperties.class)
public class KafkaTopicsConfig {

    @Bean
    public KafkaAdmin.NewTopics notificationDeadLetterTopics(TopicProperties properties) {
        return new KafkaAdmin.NewTopics(NotificationEventListener.TOPICS.stream()
                .map(Topics::deadLetterOf)
                .map(name -> TopicBuilder.name(name)
                        .partitions(properties.partitions())
                        .replicas(properties.replicas())
                        .config(TopicConfig.RETENTION_MS_CONFIG,
                                Long.toString(properties.deadLetterRetention().toMillis()))
                        .build())
                .toArray(NewTopic[]::new));
    }
}
