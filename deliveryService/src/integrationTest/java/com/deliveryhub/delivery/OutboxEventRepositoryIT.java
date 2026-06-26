package com.deliveryhub.delivery;

import com.deliveryhub.delivery.outbox.OutboxEvent;
import com.deliveryhub.delivery.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PostgresContainerConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxEventRepositoryIT {

    private static final Instant BASE = Instant.parse("2026-07-07T10:00:00Z");

    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate newTransaction;

    @BeforeEach
    void setUp() {
        newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outboxEventRepository.deleteAll();
    }

    @Test
    void storesPayloadAsJsonbAndReturnsPendingEventsOldestFirst() {
        OutboxEvent newer = outboxEventRepository.save(event("2", BASE.plusSeconds(10)));
        OutboxEvent older = outboxEventRepository.save(event("1", BASE));

        List<OutboxEvent> batch = newTransaction.execute(status -> outboxEventRepository.lockNextBatch(10));

        assertThat(batch).extracting(OutboxEvent::getId).containsExactly(older.getId(), newer.getId());
        assertThat(batch.getFirst().getPayload()).contains("\"orderId\"");
    }

    @Test
    void skipsPublishedEvents() {
        OutboxEvent event = outboxEventRepository.save(event("1", BASE));

        newTransaction.executeWithoutResult(status ->
                outboxEventRepository.lockNextBatch(10).forEach(locked -> locked.markPublished(BASE.plusSeconds(1))));

        List<OutboxEvent> pending = newTransaction.execute(status -> outboxEventRepository.lockNextBatch(10));

        assertThat(pending).isEmpty();
        assertThat(outboxEventRepository.findById(event.getId()).orElseThrow().isPublished()).isTrue();
        assertThat(outboxEventRepository.countByPublishedAtIsNull()).isZero();
    }

    @Test
    void secondRelaySkipsRowsLockedByFirst() {
        OutboxEvent first = outboxEventRepository.save(event("1", BASE));
        OutboxEvent second = outboxEventRepository.save(event("2", BASE.plusSeconds(1)));
        OutboxEvent third = outboxEventRepository.save(event("3", BASE.plusSeconds(2)));

        newTransaction.executeWithoutResult(outer -> {
            List<OutboxEvent> lockedByFirstRelay = outboxEventRepository.lockNextBatch(2);
            List<OutboxEvent> seenBySecondRelay =
                    newTransaction.execute(inner -> outboxEventRepository.lockNextBatch(2));

            assertThat(lockedByFirstRelay).extracting(OutboxEvent::getId)
                    .containsExactly(first.getId(), second.getId());
            assertThat(seenBySecondRelay).extracting(OutboxEvent::getId).containsExactly(third.getId());
        });
    }

    private static OutboxEvent event(String orderId, Instant createdAt) {
        return new OutboxEvent(UUID.randomUUID(), "Delivery", orderId, "delivery-status-updated",
                "{\"orderId\": " + orderId + "}", createdAt);
    }
}
