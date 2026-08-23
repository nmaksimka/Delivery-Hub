package com.deliveryhub.search.kafka;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.RestaurantCatalogChangedEvent;
import com.deliveryhub.search.catalog.CatalogProjection;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;

import java.io.IOException;

@Configuration
@EnableConfigurationProperties(TopicProperties.class)
public class CatalogEventListener {

    private final CatalogProjection projection;

    public CatalogEventListener(CatalogProjection projection) {
        this.projection = projection;
    }

    @KafkaListener(topics = Topics.RESTAURANT_CATALOG_CHANGED, groupId = "search-service-group")
    public void onCatalogChanged(RestaurantCatalogChangedEvent event) throws IOException {
        projection.apply(event);
    }

    @Bean
    public NewTopic catalogDeadLetterTopic(TopicProperties properties) {
        return TopicBuilder.name(Topics.deadLetterOf(Topics.RESTAURANT_CATALOG_CHANGED))
                .partitions(properties.partitions())
                .replicas(properties.replicas())
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(properties.deadLetterRetention().toMillis()))
                .build();
    }
}
