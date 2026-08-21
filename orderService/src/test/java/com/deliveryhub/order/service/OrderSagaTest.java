package com.deliveryhub.order.service;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.PaymentCapturedEvent;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.contracts.events.PaymentRejectedEvent;
import com.deliveryhub.contracts.events.PaymentReleasedEvent;
import com.deliveryhub.order.entity.CancellationReason;
import com.deliveryhub.order.entity.Order;
import com.deliveryhub.order.entity.OrderStatus;
import com.deliveryhub.order.inbox.ProcessedEvents;
import com.deliveryhub.order.outbox.Outbox;
import com.deliveryhub.order.promo.PromoService;
import com.deliveryhub.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSagaTest {

    private static final long ORDER_ID = 15L;
    private static final Instant NOW = Instant.parse("2026-08-12T18:00:00Z");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProcessedEvents processedEvents;
    @Mock
    private Outbox outbox;
    @Mock
    private PromoService promoService;

    private OrderSaga saga;
    private Order order;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        saga = new OrderSaga(orderRepository, processedEvents, new OrderCancellation(outbox, promoService, clock), clock);
        order = Order.place(42L, 1L, NOW.minusSeconds(30));
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
    }

    @Test
    void heldPaymentMovesOrderForward() {
        firstTime();

        saga.onPaymentHeld(held());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_HELD);
        verifyNoInteractions(outbox);
    }

    @Test
    void lateHeldPaymentOfCancelledOrderAsksForCompensationAgain() {
        order.cancel(CancellationReason.PAYMENT_TIMEOUT, NOW);
        firstTime();

        saga.onPaymentHeld(held());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.PAYMENT_TIMEOUT);
        verify(outbox).append(eq("Order"), eq(ORDER_ID), eq(Topics.ORDER_CANCELLED), any(), any());
    }

    @Test
    void rejectedPaymentCancelsOrderWithReason() {
        firstTime();

        saga.onPaymentRejected(new PaymentRejectedEvent(UUID.randomUUID(), ORDER_ID, 42L, BigDecimal.TEN,
                "PAYMENT_INSUFFICIENT_FUNDS", NOW));

        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.PAYMENT_REJECTED);
        verify(outbox).append(eq("Order"), eq(ORDER_ID), eq(Topics.ORDER_CANCELLED), any(), any());
    }

    @Test
    void releaseAfterDeliveryRejectionCancelsOrder() {
        order.markPaymentHeld(NOW);
        firstTime();

        saga.onPaymentReleased(new PaymentReleasedEvent(UUID.randomUUID(), ORDER_ID, 42L, BigDecimal.TEN, NOW,
                OrderSaga.RELEASED_FOR_DELIVERY));

        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.DELIVERY_REJECTED);
    }

    @Test
    void releaseOfAlreadyCancelledOrderPublishesNothing() {
        order.cancel(CancellationReason.CUSTOMER, NOW);
        firstTime();

        saga.onPaymentReleased(new PaymentReleasedEvent(UUID.randomUUID(), ORDER_ID, 42L, BigDecimal.TEN, NOW,
                "ORDER_CANCELLED"));

        assertThat(order.getCancellationReason()).isEqualTo(CancellationReason.CUSTOMER);
        verifyNoInteractions(outbox);
    }

    @Test
    void courierAndCaptureConfirmOrder() {
        order.markPaymentHeld(NOW);
        firstTime();

        saga.onCourierAssigned(new CourierAssignedEvent(UUID.randomUUID(), 7L, ORDER_ID, 9001L, NOW, 1L));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COURIER_ASSIGNED);
        saga.onPaymentCaptured(new PaymentCapturedEvent(UUID.randomUUID(), ORDER_ID, 42L, BigDecimal.TEN, NOW));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void duplicateEventIsSkipped() {
        when(processedEvents.registerFirstDelivery(any(), anyString())).thenReturn(false);

        saga.onPaymentHeld(held());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        verifyNoInteractions(orderRepository, outbox);
    }

    private void firstTime() {
        when(processedEvents.registerFirstDelivery(any(), anyString())).thenReturn(true);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
    }

    private static PaymentHeldEvent held() {
        return new PaymentHeldEvent(UUID.randomUUID(), ORDER_ID, 42L, BigDecimal.TEN, "demo", NOW, null, null, null, null, null);
    }
}
