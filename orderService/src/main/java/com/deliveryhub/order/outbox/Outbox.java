package com.deliveryhub.order.outbox;

import com.deliveryhub.contracts.EventPayloads;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Component
public class Outbox {

    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;

    public Outbox(OutboxEventRepository outboxEventRepository, Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, Long aggregateId, String topic, UUID eventId, SpecificRecord event) {
        outboxEventRepository.save(new OutboxEvent(
                eventId,
                aggregateType,
                aggregateId.toString(),
                topic,
                EventPayloads.encode(event),
                clock.instant()));
    }
}
