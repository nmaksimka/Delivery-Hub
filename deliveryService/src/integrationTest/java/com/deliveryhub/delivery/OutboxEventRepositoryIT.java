package com.deliveryhub.delivery;

import com.deliveryhub.contracts.EventPayloads;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
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

import java.time.Duration;
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
    void storesAvroPayloadAndReturnsPendingEventsOldestFirst() {
        OutboxEvent newer = outboxEventRepository.save(event("2", BASE.plusSeconds(10)));
        OutboxEvent older = outboxEventRepository.save(event("1", BASE));

        List<OutboxEvent> batch = newTransaction.execute(status -> outboxEventRepository.lockNextBatch(10));

        assertThat(batch).extracting(OutboxEvent::getId).containsExactly(older.getId(), newer.getId());
        assertThat(EventPayloads.decode("delivery-status-updated", batch.getFirst().getPayload()))
                .isInstanceOfSatisfying(DeliveryStatusUpdatedEvent.class, event -> assertThat(event.getOrderId()).isEqualTo(1L));
    }

    @Test
    void skipsPublishedEvents() {
        OutboxEvent event = outboxEventRepository.save(event("1", BASE));

        newTransaction.executeWithoutResult(status ->
                outboxEventRepository.lockNextBatch(10).forEach(locked -> locked.markPublished(BASE.plusSeconds(1))));

        List<OutboxEvent> pending = newTransaction.execute(status -> outboxEventRepository.lockNextBatch(10));

        assertThat(pending).isEmpty();
        assertThat(outboxEventRepository.findById(event.getId()).orElseThrow().isPublished()).isTrue();
        assertThat(outboxEventRepository.countByPublishedAtIsNullAndParkedAtIsNull()).isZero();
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

    @Test
    void parkedEventsAreNotOfferedToRelay() {
        OutboxEvent poison = event("1", BASE);
        poison.recordFailedAttempt("cannot decode", BASE, Duration.ofSeconds(1), 1);
        outboxEventRepository.save(poison);
        OutboxEvent healthy = outboxEventRepository.save(event("2", BASE.plusSeconds(1)));

        List<OutboxEvent> batch = newTransaction.execute(status -> outboxEventRepository.lockNextBatch(10));

        assertThat(batch).extracting(OutboxEvent::getId).containsExactly(healthy.getId());
        assertThat(outboxEventRepository.countByParkedAtIsNotNull()).isEqualTo(1);
        assertThat(outboxEventRepository.countByPublishedAtIsNullAndParkedAtIsNull()).isEqualTo(1);
        assertThat(outboxEventRepository.findById(poison.getId()).orElseThrow().getLastError())
                .isEqualTo("cannot decode");
    }

    @Test
    void onlyOneTransactionHoldsRelayLock() {
        newTransaction.executeWithoutResult(outer -> {
            assertThat(outboxEventRepository.tryLockRelay(4242L)).isTrue();

            Boolean secondInstance = newTransaction.execute(inner -> outboxEventRepository.tryLockRelay(4242L));
            Boolean otherLock = newTransaction.execute(inner -> outboxEventRepository.tryLockRelay(4243L));

            assertThat(secondInstance).isFalse();
            assertThat(otherLock).isTrue();
        });

        Boolean afterRelease = newTransaction.execute(status -> outboxEventRepository.tryLockRelay(4242L));
        assertThat(afterRelease).isTrue();
    }

    @Test
    void reportsCreationTimeOfOldestPendingEvent() {
        outboxEventRepository.save(event("2", BASE.plusSeconds(10)));
        outboxEventRepository.save(event("1", BASE));

        assertThat(outboxEventRepository.findOldestPendingCreatedAt()).contains(BASE);
    }

    private static OutboxEvent event(String orderId, Instant createdAt) {
        return new OutboxEvent(UUID.randomUUID(), "Delivery", orderId, "delivery-status-updated",
                statusUpdatedPayload(orderId), createdAt);
    }

    private static byte[] statusUpdatedPayload(String orderId) {
        return EventPayloads.encode(DeliveryStatusUpdatedEvent.newBuilder()
                .setEventId(UUID.randomUUID())
                .setDeliveryId(7L)
                .setOrderId(Long.parseLong(orderId))
                .setStatus(DeliveryStatus.ASSIGNED)
                .setUpdatedAt(BASE)
                .build());
    }
}
