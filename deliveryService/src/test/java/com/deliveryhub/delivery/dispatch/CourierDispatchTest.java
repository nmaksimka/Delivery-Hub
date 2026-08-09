package com.deliveryhub.delivery.dispatch;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.DeliveryRejectedEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.inbox.ProcessedEvents;
import com.deliveryhub.delivery.outbox.Outbox;
import com.deliveryhub.delivery.repository.CancelledOrders;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierDispatchTest {

    private static final long ORDER_ID = 100L;
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private CancelledOrders cancelledOrders;
    @Mock
    private ProcessedEvents processedEvents;
    @Mock
    private Outbox outbox;

    private CourierDispatch dispatch;

    @BeforeEach
    void setUp() {
        CourierPool pool = new CourierPool(deliveryRepository, new DispatchProperties(List.of(9001L, 9002L)));
        dispatch = new CourierDispatch(deliveryRepository, cancelledOrders, pool, processedEvents, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void assignsFirstFreeCourierAndAnnouncesIt() {
        PaymentHeldEvent event = paymentHeld();
        when(processedEvents.registerFirstDelivery(event.getEventId(), Topics.PAYMENT_HELD)).thenReturn(true);
        when(deliveryRepository.findBusyCouriers(anyCollection(), anyCollection())).thenReturn(List.of(9001L));
        when(deliveryRepository.save(any(Delivery.class))).thenAnswer(invocation -> {
            Delivery delivery = invocation.getArgument(0);
            ReflectionTestUtils.setField(delivery, "id", 7L);
            return delivery;
        });

        dispatch.onPaymentHeld(event);

        ArgumentCaptor<CourierAssignedEvent> captor = ArgumentCaptor.forClass(CourierAssignedEvent.class);
        verify(outbox).append(eq("Delivery"), eq(7L), eq(Topics.COURIER_ASSIGNED), any(), captor.capture());
        assertThat(captor.getValue().getCourierId()).isEqualTo(9002L);
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().getSequence()).isEqualTo(1L);
    }

    @Test
    void rejectsDeliveryWhenEveryCourierIsBusy() {
        PaymentHeldEvent event = paymentHeld();
        when(processedEvents.registerFirstDelivery(event.getEventId(), Topics.PAYMENT_HELD)).thenReturn(true);
        when(deliveryRepository.findBusyCouriers(anyCollection(), anyCollection())).thenReturn(List.of(9001L, 9002L));

        dispatch.onPaymentHeld(event);

        ArgumentCaptor<DeliveryRejectedEvent> captor = ArgumentCaptor.forClass(DeliveryRejectedEvent.class);
        verify(outbox).append(eq("Order"), eq(ORDER_ID), eq(Topics.DELIVERY_REJECTED), any(), captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo(CourierDispatch.NO_FREE_COURIER);
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void doesNotAssignCourierToOrderThatAlreadyHasDelivery() {
        PaymentHeldEvent event = paymentHeld();
        when(processedEvents.registerFirstDelivery(event.getEventId(), Topics.PAYMENT_HELD)).thenReturn(true);
        when(deliveryRepository.existsByOrderId(ORDER_ID)).thenReturn(true);

        dispatch.onPaymentHeld(event);

        verifyNoInteractions(outbox);
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void skipsAlreadyProcessedEvent() {
        PaymentHeldEvent event = paymentHeld();
        when(processedEvents.registerFirstDelivery(event.getEventId(), Topics.PAYMENT_HELD)).thenReturn(false);

        dispatch.onPaymentHeld(event);

        verifyNoInteractions(deliveryRepository, outbox);
    }

    @Test
    void orderCancelledBeforePaymentHeldGetsNoCourier() {
        PaymentHeldEvent event = paymentHeld();
        when(processedEvents.registerFirstDelivery(event.getEventId(), Topics.PAYMENT_HELD)).thenReturn(true);
        when(cancelledOrders.contains(ORDER_ID)).thenReturn(true);

        dispatch.onPaymentHeld(event);

        verify(deliveryRepository, never()).save(any());
        verifyNoInteractions(outbox);
    }

    private static PaymentHeldEvent paymentHeld() {
        return new PaymentHeldEvent(UUID.randomUUID(), ORDER_ID, 42L, new BigDecimal("34.00"), "demo", NOW);
    }
}
