package com.deliveryhub.order;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class DeadLetterIT {

    private static final String TOPIC = Topics.DELIVERY_STATUS_UPDATED;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private KafkaContainer kafka;

    @Test
    void unreadableRecordGoesToDeadLetterTopicByteForByte() throws Exception {
        byte[] garbage = ("not-avro-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String key = "broken-" + UUID.randomUUID();
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class))) {
            producer.send(new ProducerRecord<>(TOPIC, key, garbage)).get();
        }

        Optional<byte[]> deadLetter = new KafkaTopicReader(kafka.getBootstrapServers())
                .findFirstRaw(Topics.deadLetterOf(TOPIC), record -> key.equals(record.key()));

        assertThat(deadLetter).isPresent();
        assertThat(deadLetter.get()).isEqualTo(garbage);
    }

    @Test
    void eventThatCanNeverBeAppliedGoesToDeadLetterTopicWithoutRetries() {
        long unknownOrderId = 990_000_001L;
        DeliveryStatusUpdatedEvent event = new DeliveryStatusUpdatedEvent(
                UUID.randomUUID(), 1L, unknownOrderId, DeliveryStatus.PICKED_UP, Instant.now(), 2L);

        kafkaTemplate.send(TOPIC, Long.toString(unknownOrderId), event);

        Optional<DeliveryStatusUpdatedEvent> deadLetter = new KafkaTopicReader(kafka.getBootstrapServers())
                .findFirst(Topics.deadLetterOf(TOPIC), DeliveryStatusUpdatedEvent.class,
                        found -> found.getOrderId() == unknownOrderId);

        assertThat(deadLetter).isPresent();
        assertThat(deadLetter.get().getEventId()).isEqualTo(event.getEventId());
    }
}
