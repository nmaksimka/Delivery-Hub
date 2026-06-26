package com.deliveryhub.delivery.service;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.delivery.dto.CreateDeliveryRequest;
import com.deliveryhub.delivery.dto.DeliveryDto;
import com.deliveryhub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.inbox.ProcessedEvents;
import com.deliveryhub.delivery.mapper.DeliveryMapper;
import com.deliveryhub.delivery.outbox.Outbox;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryService")
class DeliveryServiceTest {

    private static final long ORDER_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-07-07T10:15:30Z");

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryMapper deliveryMapper;
    @Mock
    private Outbox outbox;
    @Mock
    private ProcessedEvents processedEvents;

    private DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new DeliveryService(deliveryRepository, deliveryMapper, outbox, processedEvents,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("createDeliveryFromOrderEvent")
    class FromOrderEvent {

        @Test
        @DisplayName("создаёт доставку в статусе ASSIGNED и кладёт событие в outbox")
        void createsAssignedDeliveryAndAppendsEvent() {
            OrderCreatedEvent event = orderCreated();
            when(processedEvents.registerFirstDelivery(event.eventId(), Topics.ORDER_CREATED)).thenReturn(true);
            when(deliveryRepository.existsByOrderId(ORDER_ID)).thenReturn(false);
            when(deliveryRepository.save(any(Delivery.class))).thenAnswer(inv -> {
                Delivery delivery = inv.getArgument(0);
                ReflectionTestUtils.setField(delivery, "id", 7L);
                return delivery;
            });

            deliveryService.createDeliveryFromOrderEvent(event);

            DeliveryStatusUpdatedEvent published = appendedEvent(7L);
            assertThat(published.eventId()).isNotNull().isNotEqualTo(event.eventId());
            assertThat(published.orderId()).isEqualTo(ORDER_ID);
            assertThat(published.deliveryId()).isEqualTo(7L);
            assertThat(published.status()).isEqualTo(DeliveryStatus.ASSIGNED);
            assertThat(published.updatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("событие с уже обработанным eventId пропускается целиком")
        void skipsAlreadyProcessedEvent() {
            OrderCreatedEvent event = orderCreated();
            when(processedEvents.registerFirstDelivery(event.eventId(), Topics.ORDER_CREATED)).thenReturn(false);

            deliveryService.createDeliveryFromOrderEvent(event);

            verifyNoInteractions(deliveryRepository, outbox);
        }

        @Test
        @DisplayName("новое событие по заказу с существующей доставкой не создаёт дубль")
        void doesNotDuplicateDeliveryForSameOrder() {
            OrderCreatedEvent event = orderCreated();
            when(processedEvents.registerFirstDelivery(event.eventId(), Topics.ORDER_CREATED)).thenReturn(true);
            when(deliveryRepository.existsByOrderId(ORDER_ID)).thenReturn(true);

            deliveryService.createDeliveryFromOrderEvent(event);

            verify(deliveryRepository, never()).save(any());
            verifyNoInteractions(outbox);
        }
    }

    @Nested
    @DisplayName("updateDeliveryStatus")
    class UpdateStatus {

        @Test
        @DisplayName("кладёт событие в outbox, чтобы заказ узнал о новом статусе")
        void appendsEventOnManualUpdate() {
            Delivery delivery = existingDelivery();
            when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
            when(deliveryMapper.toDto(delivery)).thenReturn(dto());

            deliveryService.updateDeliveryStatus(1L, new UpdateDeliveryStatusRequest(DeliveryStatus.DELIVERED, null));

            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
            assertThat(appendedEvent(1L).status()).isEqualTo(DeliveryStatus.DELIVERED);
        }

        @Test
        @DisplayName("обновляет курьера, если он передан")
        void updatesCourierWhenProvided() {
            Delivery delivery = existingDelivery();
            when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
            when(deliveryMapper.toDto(delivery)).thenReturn(dto());

            deliveryService.updateDeliveryStatus(1L, new UpdateDeliveryStatusRequest(DeliveryStatus.PICKED_UP, 55L));

            assertThat(delivery.getCourierId()).isEqualTo(55L);
        }

        @Test
        @DisplayName("недопустимый переход не оставляет события в outbox")
        void rejectedTransitionAppendsNothing() {
            Delivery delivery = existingDelivery();
            delivery.changeStatus(DeliveryStatus.DELIVERED);
            when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

            assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(1L,
                    new UpdateDeliveryStatusRequest(DeliveryStatus.IN_TRANSIT, null)))
                    .isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(outbox);
        }

        @Test
        @DisplayName("бросает EntityNotFoundException для несуществующей доставки")
        void failsWhenMissing() {
            when(deliveryRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(404L,
                    new UpdateDeliveryStatusRequest(DeliveryStatus.DELIVERED, null)))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("createDelivery")
    class CreateDelivery {

        @Test
        @DisplayName("не даёт создать вторую доставку на тот же заказ")
        void rejectsDuplicate() {
            when(deliveryRepository.existsByOrderId(ORDER_ID)).thenReturn(true);

            assertThatThrownBy(() -> deliveryService.createDelivery(new CreateDeliveryRequest(ORDER_ID, null, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");
        }
    }

    private DeliveryStatusUpdatedEvent appendedEvent(long deliveryId) {
        ArgumentCaptor<DeliveryStatusUpdatedEvent> captor = ArgumentCaptor.forClass(DeliveryStatusUpdatedEvent.class);
        verify(outbox).append(eq("Delivery"), eq(deliveryId), eq(Topics.DELIVERY_STATUS_UPDATED), captor.capture());
        return captor.getValue();
    }

    private static OrderCreatedEvent orderCreated() {
        return new OrderCreatedEvent(UUID.randomUUID(), ORDER_ID, null, null, null, null, List.of());
    }

    private static Delivery existingDelivery() {
        Delivery delivery = Delivery.forOrder(ORDER_ID);
        ReflectionTestUtils.setField(delivery, "id", 1L);
        return delivery;
    }

    private static DeliveryDto dto() {
        return new DeliveryDto(1L, ORDER_ID, null, DeliveryStatus.ASSIGNED, null, Instant.EPOCH, Instant.EPOCH);
    }
}
