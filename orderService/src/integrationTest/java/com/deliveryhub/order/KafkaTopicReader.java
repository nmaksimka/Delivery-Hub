package com.deliveryhub.order;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class KafkaTopicReader {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String bootstrapServers;

    public KafkaTopicReader(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public Optional<ConsumerRecord<String, String>> findFirst(String topic,
                                                              Predicate<ConsumerRecord<String, String>> filter) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Instant deadline = Instant.now().plus(TIMEOUT);
        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (filter.test(record)) {
                        return Optional.of(record);
                    }
                }
            }
        }
        return Optional.empty();
    }
}
