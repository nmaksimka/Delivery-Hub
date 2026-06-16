package com.deliveryhub.delivery.service;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.delivery.dto.CreateDeliveryRequest;
import com.deliveryhub.delivery.dto.DeliveryDto;
import com.deliveryhub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.event.DeliveryStatusChangedDomainEvent;
import com.deliveryhub.delivery.mapper.DeliveryMapper;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeliveryService")
class DeliveryServiceTest {

    private static final long ORDER_ID = 100L;

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private DeliveryMapper deliveryMapper;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private DeliveryService deliveryService;

    @Nested
    @DisplayName("createDeliveryFromOrderEvent")
    class FromOrderEvent {

        @Test
        @DisplayName("создаёт доставку в статусе ASSIGNED")
        void createsAssignedDelivery() {
            when(deliveryRepository.existsByOrderId(ORDER_ID)).thenReturn(false);
            when(deliveryRepository.save(any(Delivery.class))).thenAnswer(inv -> inv.getArgument(0));

            deliveryService.createDeliveryFromOrderEvent(
                    OrderCreatedEvent.builder().orderId(ORDER_ID).build());

            ArgumentCaptor<Delivery> captor = ArgumentCaptor.forClass(Delivery.class);
            verify(deliveryRepository).save(captor.capture());
            assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
            assertThat(captor.getValue().getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        }

        @Test
        @DisplayName("идемпотентен: повторное событие не создаёт дубль")
        void isIdempotent() {
            when(deliveryRepository.existsByOrderId(ORDER_ID)).thenReturn(true);

            deliveryService.createDeliveryFromOrderEvent(
                    OrderCreatedEvent.builder().orderId(ORDER_ID).build());

            verify(deliveryRepository, never()).save(any());
            verify(applicationEventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("публикует событие об изменении статуса")
        void publishesStatusEvent() {
            when(deliveryRepository.existsByOrderId(ORDER_ID)).thenReturn(false);
            when(deliveryRepository.save(any(Delivery.class))).thenAnswer(inv -> {
                Delivery delivery = inv.getArgument(0);
                delivery.setId(7L);
                delivery.setUpdatedAt(Instant.now());
                return delivery;
            });

            deliveryService.createDeliveryFromOrderEvent(
                    OrderCreatedEvent.builder().orderId(ORDER_ID).build());

            ArgumentCaptor<DeliveryStatusChangedDomainEvent> captor =
                    ArgumentCaptor.forClass(DeliveryStatusChangedDomainEvent.class);
            verify(applicationEventPublisher).publishEvent(captor.capture());

            assertThat(captor.getValue().payload().getOrderId()).isEqualTo(ORDER_ID);
            assertThat(captor.getValue().payload().getDeliveryId()).isEqualTo(7L);
            assertThat(captor.getValue().payload().getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        }
    }

    @Nested
    @DisplayName("updateDeliveryStatus")
    class UpdateStatus {

        @Test
        @DisplayName("публикует событие, чтобы заказ узнал о новом статусе")
        void publishesEventOnManualUpdate() {
            Delivery delivery = existingDelivery();
            when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
            when(deliveryMapper.toDto(delivery)).thenReturn(new DeliveryDto());

            deliveryService.updateDeliveryStatus(1L, UpdateDeliveryStatusRequest.builder()
                    .status(DeliveryStatus.DELIVERED)
                    .build());

            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);

            ArgumentCaptor<DeliveryStatusChangedDomainEvent> captor =
                    ArgumentCaptor.forClass(DeliveryStatusChangedDomainEvent.class);
            verify(applicationEventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().payload().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        }

        @Test
        @DisplayName("обновляет курьера, если он передан")
        void updatesCourierWhenProvided() {
            Delivery delivery = existingDelivery();
            when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
            when(deliveryMapper.toDto(delivery)).thenReturn(new DeliveryDto());

            deliveryService.updateDeliveryStatus(1L, UpdateDeliveryStatusRequest.builder()
                    .status(DeliveryStatus.PICKED_UP)
                    .courierId(55L)
                    .build());

            assertThat(delivery.getCourierId()).isEqualTo(55L);
        }

        @Test
        @DisplayName("бросает EntityNotFoundException для несуществующей доставки")
        void failsWhenMissing() {
            when(deliveryRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deliveryService.updateDeliveryStatus(404L,
                    UpdateDeliveryStatusRequest.builder().status(DeliveryStatus.DELIVERED).build()))
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

            assertThatThrownBy(() -> deliveryService.createDelivery(
                    CreateDeliveryRequest.builder().orderId(ORDER_ID).build()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");
        }
    }

    private static Delivery existingDelivery() {
        return Delivery.builder()
                .id(1L)
                .orderId(ORDER_ID)
                .status(DeliveryStatus.ASSIGNED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
