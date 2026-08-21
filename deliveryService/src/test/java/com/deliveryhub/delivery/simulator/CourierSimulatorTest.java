package com.deliveryhub.delivery.simulator;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.quote.Polyline;
import com.deliveryhub.delivery.quote.Route;
import com.deliveryhub.delivery.quote.RoutePath;
import com.deliveryhub.delivery.repository.CourierRepository;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import com.deliveryhub.delivery.service.DeliveryService;
import com.deliveryhub.delivery.tracking.CourierTracking;
import com.deliveryhub.delivery.tracking.DeliveryRoutes;
import com.deliveryhub.delivery.tracking.TrackedRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierSimulatorTest {

    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");
    private static final GeoPosition RESTAURANT = new GeoPosition(56.4700, 84.9500);
    private static final GeoPosition CUSTOMER = new GeoPosition(56.4790, 84.9500);
    private static final SimulatorProperties PROPERTIES =
            new SimulatorProperties(true, Duration.ofSeconds(2), 36, Duration.ofSeconds(15));

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private CourierRepository courierRepository;
    @Mock
    private DeliveryService deliveryService;
    @Mock
    private CourierTracking courierTracking;
    @Mock
    private DeliveryRoutes routes;

    private CourierSimulator simulator;

    @BeforeEach
    void setUp() {
        simulator = new CourierSimulator(deliveryRepository, courierRepository, deliveryService, courierTracking,
                routes, PROPERTIES, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void picksUpOrdersConfirmedLongerThanDelayAgo() {
        Delivery delivery = delivery(DeliveryStatus.ASSIGNED);
        when(deliveryRepository.findSimulatedAwaitingPickup(NOW.minusSeconds(15))).thenReturn(List.of(delivery));

        simulator.tick();

        verify(deliveryService).updateDeliveryStatus(1L, new UpdateDeliveryStatusRequest(DeliveryStatus.PICKED_UP, null));
    }

    @Test
    void pickedUpCourierStartsAtRestaurantAndMovesBySpeed() {
        Delivery delivery = delivery(DeliveryStatus.PICKED_UP);
        Courier courier = courier();
        when(deliveryRepository.findSimulatedOnTheWay()).thenReturn(List.of(delivery));
        when(courierRepository.findById(9001L)).thenReturn(Optional.of(courier));
        when(routes.plan(eq(1L), eq(RESTAURANT), eq(CUSTOMER), anyDouble())).thenReturn(straightRoute());

        simulator.tick();

        ArgumentCaptor<GeoPosition> position = ArgumentCaptor.forClass(GeoPosition.class);
        var order = inOrder(deliveryService, courierTracking);
        order.verify(deliveryService).updateDeliveryStatus(1L,
                new UpdateDeliveryStatusRequest(DeliveryStatus.IN_TRANSIT, null));
        order.verify(courierTracking).report(eq(courier), position.capture(), eq(0.0));
        assertThat(Polyline.meters(RESTAURANT, position.getValue())).isCloseTo(20, within(0.01));
        verify(deliveryService, never()).updateDeliveryStatus(1L,
                new UpdateDeliveryStatusRequest(DeliveryStatus.DELIVERED, null));
    }

    @Test
    void courierAtCustomerFinishesDeliveryThroughDomainTransition() {
        Delivery delivery = delivery(DeliveryStatus.IN_TRANSIT);
        Courier courier = courier();
        when(deliveryRepository.findSimulatedOnTheWay()).thenReturn(List.of(delivery));
        when(courierRepository.findById(9001L)).thenReturn(Optional.of(courier));
        GeoPosition almostThere = new GeoPosition(56.47899, 84.9500);
        when(routes.plan(eq(1L), eq(RESTAURANT), eq(CUSTOMER), anyDouble())).thenReturn(new TrackedRoute(
                new RoutePath(Polyline.straight(almostThere, CUSTOMER), 1, Route.RouteSource.OSRM), 10.0));

        simulator.tick();

        verify(courierTracking).report(courier, CUSTOMER, 0.0);
        verify(deliveryService).updateDeliveryStatus(1L,
                new UpdateDeliveryStatusRequest(DeliveryStatus.DELIVERED, null));
    }

    @Test
    void deliveryWithoutCustomerPointIsNotDriven() {
        Delivery delivery = Delivery.forOrder(5L, 42L, 9001L, null, NOW);
        ReflectionTestUtils.setField(delivery, "id", 1L);
        delivery.changeStatus(DeliveryStatus.PICKED_UP, NOW);
        when(deliveryRepository.findSimulatedOnTheWay()).thenReturn(List.of(delivery));
        when(courierRepository.findById(9001L)).thenReturn(Optional.of(courier()));

        simulator.tick();

        verify(routes, never()).plan(anyLong(), any(), any(), anyDouble());
        verify(courierTracking, never()).report(any(), any(), any());
    }

    private static TrackedRoute straightRoute() {
        return new TrackedRoute(new RoutePath(Polyline.straight(RESTAURANT, CUSTOMER), 100, Route.RouteSource.OSRM),
                10.0);
    }

    private static Delivery delivery(DeliveryStatus status) {
        Delivery delivery = Delivery.forOrder(5L, 42L, 9001L, null, NOW);
        ReflectionTestUtils.setField(delivery, "id", 1L);
        delivery.routeBetween(RESTAURANT, CUSTOMER);
        if (status.ordinal() >= DeliveryStatus.PICKED_UP.ordinal()) {
            delivery.changeStatus(DeliveryStatus.PICKED_UP, NOW);
        }
        if (status == DeliveryStatus.IN_TRANSIT) {
            delivery.changeStatus(DeliveryStatus.IN_TRANSIT, NOW);
        }
        return delivery;
    }

    private static Courier courier() {
        Courier courier = Courier.register(null, "Алексей", "+79990009001", NOW);
        ReflectionTestUtils.setField(courier, "id", 9001L);
        return courier;
    }
}
