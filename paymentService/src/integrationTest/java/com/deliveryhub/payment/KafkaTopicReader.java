package com.deliveryhub.payment;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
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

    public <T extends SpecificRecord> Optional<T> findFirst(String topic, Class<T> eventType, Predicate<T> filter) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class,
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, KafkaContainerConfig.SCHEMA_REGISTRY_URL,
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true,
                KafkaAvroDeserializerConfig.AVRO_USE_LOGICAL_TYPE_CONVERTERS_CONFIG, true);
        Instant deadline = Instant.now().plus(TIMEOUT);
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, Object> record : consumer.poll(Duration.ofMillis(500))) {
                    T event = eventType.cast(record.value());
                    if (filter.test(event)) {
                        return Optional.of(event);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public Optional<byte[]> findFirstRaw(String topic, Predicate<ConsumerRecord<String, byte[]>> filter) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "it-raw-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        Instant deadline = Instant.now().plus(TIMEOUT);
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    if (filter.test(record)) {
                        return Optional.of(record.value());
                    }
                }
            }
        }
        return Optional.empty();
    }
}
