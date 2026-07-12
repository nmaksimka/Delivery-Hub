package com.deliveryhub.delivery;

import com.deliveryhub.delivery.cleanup.RetentionCleanup;
import com.deliveryhub.delivery.outbox.OutboxEvent;
import com.deliveryhub.delivery.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class RetentionCleanupIT {

    @Autowired
    private RetentionCleanup cleanup;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void removesOldPublishedEventsAndInboxRecordsButKeepsFreshAndParked() {
        Instant now = Instant.now();
        OutboxEvent oldPublished = published(now.minus(Duration.ofDays(8)));
        OutboxEvent freshPublished = published(now.minus(Duration.ofDays(1)));
        OutboxEvent oldParked = new OutboxEvent(UUID.randomUUID(), "Test", "cleanup", "no-such-topic",
                new byte[]{1}, now.minus(Duration.ofDays(30)));
        oldParked.recordFailedAttempt("poison", now.minus(Duration.ofDays(30)), Duration.ofSeconds(1), 1);
        outboxEventRepository.save(oldParked);
        UUID oldRecord = processedAt(now.minus(Duration.ofDays(15)));
        UUID freshRecord = processedAt(now.minus(Duration.ofDays(2)));

        cleanup.removeExpired();

        assertThat(outboxEventRepository.existsById(oldPublished.getId())).isFalse();
        assertThat(outboxEventRepository.existsById(freshPublished.getId())).isTrue();
        assertThat(outboxEventRepository.existsById(oldParked.getId())).isTrue();
        assertThat(isProcessed(oldRecord)).isFalse();
        assertThat(isProcessed(freshRecord)).isTrue();
    }

    private OutboxEvent published(Instant at) {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "Test", "cleanup", "no-such-topic", new byte[]{1}, at);
        event.markPublished(at);
        return outboxEventRepository.save(event);
    }

    private UUID processedAt(Instant at) {
        UUID eventId = UUID.randomUUID();
        jdbcClient.sql("INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, ?)")
                .params(eventId, "cleanup-test", Timestamp.from(at))
                .update();
        return eventId;
    }

    private boolean isProcessed(UUID eventId) {
        return jdbcClient.sql("SELECT count(*) FROM processed_events WHERE event_id = ?")
                .param(eventId)
                .query(Long.class)
                .single() == 1;
    }
}
