package com.deliveryhub.order.outbox;

import com.deliveryhub.contracts.events.IntegrationEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@Component
public class Outbox {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public Outbox(OutboxEventRepository outboxEventRepository, JsonMapper jsonMapper, Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, Long aggregateId, String eventType, IntegrationEvent event) {
        outboxEventRepository.save(new OutboxEvent(
                event.eventId(),
                aggregateType,
                aggregateId.toString(),
                eventType,
                jsonMapper.writeValueAsString(event),
                clock.instant()));
    }
}
