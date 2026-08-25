package com.deliveryhub.restaurant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;

@TestConfiguration(proxyBeanMethods = false)
public class KafkaContainerConfig {

    public static final String SCHEMA_REGISTRY_URL = "mock://restaurant-it";

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.1.2");
    }

    @Bean
    DynamicPropertyRegistrar kafkaForListeners() {
        return registry -> {
            registry.add("spring.kafka.properties.schema.registry.url", () -> SCHEMA_REGISTRY_URL);
            registry.add("spring.kafka.listener.auto-startup", () -> true);
            registry.add("spring.kafka.admin.auto-create", () -> true);
            registry.add("app.kafka.topics.partitions", () -> 1);
        };
    }
}
