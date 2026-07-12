package com.deliveryhub.delivery;

import com.deliveryhub.contracts.Topics;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class DeadLetterIT {

    private static final String TOPIC = Topics.ORDER_CREATED;

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
}
