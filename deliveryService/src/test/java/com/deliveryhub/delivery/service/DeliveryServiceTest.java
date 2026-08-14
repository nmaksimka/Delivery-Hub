package com.deliveryhub.delivery.service;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCancelledEvent;
import com.deliveryhub.delivery.dto.CreateDeliveryRequest;
import com.deliveryhub.delivery.dto.DeliveryDto;
import com.deliveryhub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.CourierStatus;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.exception.DeliveryAlreadyExistsException;
import com.deliveryhub.delivery.exception.InvalidStatusTransitionException;
import com.deliveryhub.delivery.inbox.ProcessedEvents;
import com.deliveryhub.delivery.mapper.DeliveryMapper;
import com.deliveryhub.delivery.outbox.Outbox;
import com.deliveryhub.delivery.repository.CancelledOrders;
import com.deliveryhub.delivery.repository.CourierRepository;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    @Mock
    private CancelledOrders cancelledOrders;
    @Mock
    private CourierRepository courierRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new DeliveryService(deliveryRepository, deliveryMapper, outbox, processedEvents, cancelledOrders,
                courierRepository, Clock.fixed(NOW, ZoneOffset.UTC), meterRegistry);
    }

    @Nested
    @DisplayName("cancelDeliveryOfOrder")
    class CancelDeliveryOfOrder {

        @Test
        @DisplayName("отменяет доставку и сообщает об этом событием")
        void cancelsDeliveryAndAppendsEvent() {
            Delivery delivery = existingDelivery();
            delivery.acceptOrderEvent(1L);
            OrderCancelledEvent event = orderCancelled(2L);
            when(processedEvents.registerFirstDelivery(event.getEventId(), Topics.ORDER_CANCELLED)).thenReturn(true);
            when(deliveryRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(delivery));

            deliveryService.cancelDeliveryOfOrder(event);

            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
            assertThat(appendedEvent(1L).getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        }

        @Test
        @DisplayName("отмена освобождает курьера")
        void cancellationReleasesCourier() {
            Delivery delivery = deliveryWithCourier(9001L);
            Courier courier = busyCourier();
            OrderCancelledEvent event = orderCancelled(2L);
            when(processedEvents.registerFirstDelivery(event.getEventId(), Topics.ORDER_CANCELLED)).thenReturn(true);
            when(deliveryRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(delivery));
            when(courierRepository.findById(9001L)).thenReturn(Optional.of(courier));

            deliveryService.cancelDeliveryOfOrder(event);

            assertThat(courier.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
        }

        @Test
        @DisplayName("устаревшая отмена отбрасывается")
        void dropsStaleCancellation() {
            Delivery delivery = existingDelivery();
            delivery.acceptOrderEvent(5L);
            OrderCancelledEvent event = orderCancelled(2L);
            when(processedEvents.registerFirstDelivery(event.getEventId(), Topics.ORDER_CANCELLED)).thenReturn(true);
            when(deliveryRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(delivery));

            deliveryService.cancelDeliveryOfOrder(event);

            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
            verifyNoInteractions(outbox);
        }

        @Test
        @DisplayName("завершённая доставка не меняется")
        void finishedDeliveryStaysAsIs() {
            Delivery delivery = existingDelivery();
            ReflectionTestUtils.setField(delivery, "status", DeliveryStatus.DELIVERED);
            OrderCancelledEvent event = orderCancelled(2L);
            when(processedEvents.registerFirstDelivery(event.getEventId(), Topics.ORDER_CANCELLED)).thenReturn(true);
            when(deliveryRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(delivery));

            deliveryService.cancelDeliveryOfOrder(event);

            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
            verifyNoInteractions(outbox);
        }

        @Test
        @DisplayName("отмена раньше создания доставки запоминается, чтобы курьера потом не назначили")
        void remembersCancellationBeforeDeliveryExists() {
            OrderCancelledEvent event = orderCancelled(2L);
            when(processedEvents.registerFirstDelivery(event.getEventId(), Topics.ORDER_CANCELLED)).thenReturn(true);
            when(deliveryRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            deliveryService.cancelDeliveryOfOrder(event);

            verify(cancelledOrders).remember(ORDER_ID, NOW);
            verify(deliveryRepository, never()).save(any());
            verifyNoInteractions(outbox);
        }

        private OrderCancelledEvent orderCancelled(long sequence) {
            return new OrderCancelledEvent(UUID.randomUUID(), ORDER_ID, 42L, NOW, sequence);
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

            deliveryService.updateDeliveryStatus(1L, new UpdateDeliveryStatusRequest(DeliveryStatus.PICKED_UP, null));

            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
            assertThat(appendedEvent(1L).getStatus()).isEqualTo(DeliveryStatus.PICKED_UP);
        }

        @Test
        @DisplayName("повтор того же статуса не порождает событие")
        void sameStatusAppendsNothing() {
            Delivery delivery = existingDelivery();
            when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

            deliveryService.updateDeliveryStatus(1L, new UpdateDeliveryStatusRequest(DeliveryStatus.ASSIGNED, null));

            verifyNoInteractions(outbox);
        }

        @Test
        @DisplayName("курьера можно назначить, не меняя статус")
        void assignsCourierWithoutStatus() {
            Delivery delivery = existingDelivery();
            when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

            deliveryService.updateDeliveryStatus(1L, new UpdateDeliveryStatusRequest(null, 55L));

            assertThat(delivery.getCourierId()).isEqualTo(55L);
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
            verifyNoInteractions(outbox);
        }

        @Test
        @DisplayName("считает сорванные доставки в метрике с тегом статуса")
        void countsCancelledDeliveries() {
            Delivery delivery = existingDelivery();
            when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

            deliveryService.updateDeliveryStatus(1L, new UpdateDeliveryStatusRequest(DeliveryStatus.CANCELLED, null));

            assertThat(meterRegistry.get(DeliveryService.STATUS_CHANGED)
                    .tag(DeliveryService.STATUS_TAG, "CANCELLED").counter().count()).isEqualTo(1.0);
            assertThat(meterRegistry.find(DeliveryService.STATUS_CHANGED)
                    .tag(DeliveryService.STATUS_TAG, "DELIVERED").counter()).isNull();
        }

        @Test
        @DisplayName("доставленный заказ освобождает курьера")
        void deliveredReleasesCourier() {
            Delivery delivery = deliveryWithCourier(9001L);
            ReflectionTestUtils.setField(delivery, "status", DeliveryStatus.IN_TRANSIT);
            Courier courier = busyCourier();
            when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
            when(courierRepository.findById(9001L)).thenReturn(Optional.of(courier));

            deliveryService.updateDeliveryStatus(1L, new UpdateDeliveryStatusRequest(DeliveryStatus.DELIVERED, null));

            assertThat(courier.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
        }

        @Test
        @DisplayName("смена курьера освобождает прежнего и занимает нового")
        void reassignmentMovesBusyFlag() {
            Delivery delivery = deliveryWithCourier(9001L);
            Courier previous = busyCourier();
            Courier next = Courier.register(null, "Ирина", "+79990000002", NOW);
            next.goOnline(NOW);
            when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
            when(courierRepository.findById(9001L)).thenReturn(Optional.of(previous));
            when(courierRepository.findById(9002L)).thenReturn(Optional.of(next));

            deliveryService.updateDeliveryStatus(1L, new UpdateDeliveryStatusRequest(null, 9002L));

            assertThat(previous.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
            assertThat(next.getStatus()).isEqualTo(CourierStatus.BUSY);
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
            when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

            assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(1L,
                    new UpdateDeliveryStatusRequest(DeliveryStatus.DELIVERED, null)))
                    .isInstanceOf(InvalidStatusTransitionException.class);
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

            assertThatThrownBy(() -> deliveryService.createDelivery(new CreateDeliveryRequest(ORDER_ID, null, null, null)))
                    .isInstanceOf(DeliveryAlreadyExistsException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("createDelivery при гонке")
    class CreateDeliveryRace {

        @Test
        @DisplayName("нарушение уникальности order_id превращается в доменное исключение")
        void translatesUniqueViolation() {
            when(deliveryRepository.existsByOrderId(ORDER_ID)).thenReturn(false);
            when(deliveryRepository.saveAndFlush(any(Delivery.class)))
                    .thenThrow(new DataIntegrityViolationException("deliveries_order_id_key"));

            assertThatThrownBy(() -> deliveryService.createDelivery(
                    new CreateDeliveryRequest(ORDER_ID, null, null, null)))
                    .isInstanceOf(DeliveryAlreadyExistsException.class);
            verifyNoInteractions(outbox);
        }
    }

    private DeliveryStatusUpdatedEvent appendedEvent(long deliveryId) {
        ArgumentCaptor<DeliveryStatusUpdatedEvent> captor = ArgumentCaptor.forClass(DeliveryStatusUpdatedEvent.class);
        verify(outbox).append(eq("Delivery"), eq(deliveryId), eq(Topics.DELIVERY_STATUS_UPDATED), any(),
                captor.capture());
        return captor.getValue();
    }

    private static Delivery deliveryWithCourier(long courierId) {
        Delivery delivery = Delivery.forOrder(ORDER_ID, 42L, courierId, null, NOW);
        ReflectionTestUtils.setField(delivery, "id", 1L);
        return delivery;
    }

    private static Courier busyCourier() {
        Courier courier = Courier.register(null, "Алексей", "+79990000001", NOW);
        courier.occupy(NOW);
        return courier;
    }

    private static Delivery existingDelivery() {
        Delivery delivery = Delivery.forOrder(ORDER_ID, 42L, NOW);
        ReflectionTestUtils.setField(delivery, "id", 1L);
        return delivery;
    }

    private static DeliveryDto dto() {
        return new DeliveryDto(1L, ORDER_ID, 42L, null, DeliveryStatus.ASSIGNED, null, Instant.EPOCH, Instant.EPOCH);
    }
}
