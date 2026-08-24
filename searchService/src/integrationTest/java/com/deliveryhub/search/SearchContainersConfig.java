package com.deliveryhub.search;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.kafka.KafkaContainer;

@TestConfiguration(proxyBeanMethods = false)
public class SearchContainersConfig {

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.1.2");
    }

    @Bean
    @ServiceConnection
    ElasticsearchContainer elasticsearch() {
        return new ElasticsearchContainer("elasticsearch:9.2.8")
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
    }

    @Bean
    DynamicPropertyRegistrar inMemorySchemaRegistry() {
        return registry -> {
            registry.add("spring.kafka.properties.schema.registry.url", () -> "mock://search-it");
            registry.add("app.kafka.topics.partitions", () -> 1);
        };
    }
}
