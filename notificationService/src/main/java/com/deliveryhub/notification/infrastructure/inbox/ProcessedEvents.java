package com.deliveryhub.notification.infrastructure.inbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class ProcessedEvents {

    private static final String INSERT_IF_ABSENT = """
            INSERT INTO processed_events (event_id, event_type, processed_at)
            VALUES (:eventId, :eventType, :processedAt)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final JdbcClient jdbcClient;
    private final Clock clock;

    public ProcessedEvents(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean registerFirstDelivery(UUID eventId, String eventType) {
        int inserted = jdbcClient.sql(INSERT_IF_ABSENT)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("processedAt", Timestamp.from(clock.instant()))
                .update();
        return inserted == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteProcessedBefore(Instant before) {
        return jdbcClient.sql("DELETE FROM processed_events WHERE processed_at < :before")
                .param("before", Timestamp.from(before))
                .update();
    }
}
