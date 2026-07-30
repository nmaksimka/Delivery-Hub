package com.deliveryhub.delivery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.kafka.topics")
public record TopicProperties(
        @DefaultValue("3") int partitions,
        @DefaultValue("1") short replicas,
        @DefaultValue("7d") Duration retention,
        @DefaultValue("30d") Duration deadLetterRetention) {
}
