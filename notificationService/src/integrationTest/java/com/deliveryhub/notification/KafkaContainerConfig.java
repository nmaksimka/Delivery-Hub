package com.deliveryhub.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;

@TestConfiguration(proxyBeanMethods = false)
public class KafkaContainerConfig {

    public static final String SCHEMA_REGISTRY_URL = "mock://deliveryhub-it";

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.1.2");
    }

    @Bean
    DynamicPropertyRegistrar inMemorySchemaRegistry() {
        return registry -> {
            registry.add("spring.kafka.properties.schema.registry.url", () -> SCHEMA_REGISTRY_URL);
            registry.add("app.kafka.topics.partitions", () -> 1);
        };
    }
}
