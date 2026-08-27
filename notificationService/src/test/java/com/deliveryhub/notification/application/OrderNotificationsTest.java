package com.deliveryhub.notification.application;

import com.deliveryhub.notification.domain.Notification;
import com.deliveryhub.notification.domain.NotificationRepository;
import com.deliveryhub.notification.domain.NotificationType;
import com.deliveryhub.notification.domain.OrderRecipient;
import com.deliveryhub.notification.domain.OrderRecipientRepository;
import com.deliveryhub.notification.infrastructure.inbox.ProcessedEvents;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderNotificationsTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final OrderRecipientRepository recipients = mock(OrderRecipientRepository.class);
    private final ProcessedEvents processedEvents = mock(ProcessedEvents.class);
    private final OrderNotifications orderNotifications = new OrderNotifications(notifications, recipients,
            processedEvents, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void orderCreatedRemembersRecipientAndStoresNotification() {
        UUID eventId = UUID.randomUUID();
        when(processedEvents.registerFirstDelivery(eventId, "order-created")).thenReturn(true);

        orderNotifications.orderCreated(eventId, 42L, 7L, new BigDecimal("500.00"));

        ArgumentCaptor<OrderRecipient> recipient = ArgumentCaptor.forClass(OrderRecipient.class);
        verify(recipients).save(recipient.capture());
        assertThat(recipient.getValue().getUserId()).isEqualTo(7L);
        Notification saved = savedNotification();
        assertThat(saved.getId()).isEqualTo(eventId);
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CREATED);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void duplicateEventIsIgnored() {
        when(processedEvents.registerFirstDelivery(any(), anyString())).thenReturn(false);

        orderNotifications.paymentRejected(UUID.randomUUID(), 42L, 7L, BigDecimal.ONE, "NO_MONEY");

        verify(notifications, never()).save(any());
    }

    @Test
    void courierAssignedGoesToOrderOwner() {
        UUID eventId = UUID.randomUUID();
        when(processedEvents.registerFirstDelivery(eventId, "courier-assigned")).thenReturn(true);
        when(recipients.findById(42L)).thenReturn(Optional.of(new OrderRecipient(42L, 7L, NOW)));

        orderNotifications.courierAssigned(eventId, 42L);

        Notification saved = savedNotification();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getType()).isEqualTo(NotificationType.COURIER_ASSIGNED);
    }

    @Test
    void deliveredOrderWithUnknownOwnerIsSkipped() {
        when(processedEvents.registerFirstDelivery(any(), anyString())).thenReturn(true);
        when(recipients.findById(42L)).thenReturn(Optional.empty());

        orderNotifications.orderDelivered(UUID.randomUUID(), 42L);

        verify(notifications, never()).save(any());
    }

    private Notification savedNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        return captor.getValue();
    }
}
