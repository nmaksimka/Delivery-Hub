package com.deliveryhub.delivery.simulator;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.quote.Polyline;
import com.deliveryhub.delivery.repository.CourierRepository;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import com.deliveryhub.delivery.service.DeliveryService;
import com.deliveryhub.delivery.tracking.CourierTracking;
import com.deliveryhub.delivery.tracking.DeliveryRoutes;
import com.deliveryhub.delivery.tracking.TrackedRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@ConditionalOnBooleanProperty("app.simulator.enabled")
@EnableConfigurationProperties(SimulatorProperties.class)
public class CourierSimulator {

    private static final Logger log = LoggerFactory.getLogger(CourierSimulator.class);

    private final DeliveryRepository deliveryRepository;
    private final CourierRepository courierRepository;
    private final DeliveryService deliveryService;
    private final CourierTracking courierTracking;
    private final DeliveryRoutes routes;
    private final SimulatorProperties properties;
    private final Clock clock;
    private final Map<Long, Trip> trips = new HashMap<>();
    private final Set<Long> stranded = new HashSet<>();

    public CourierSimulator(DeliveryRepository deliveryRepository, CourierRepository courierRepository,
                            DeliveryService deliveryService, CourierTracking courierTracking, DeliveryRoutes routes,
                            SimulatorProperties properties, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.courierRepository = courierRepository;
        this.deliveryService = deliveryService;
        this.courierTracking = courierTracking;
        this.routes = routes;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.simulator.tick}")
    public void tick() {
        pickUpConfirmedOrders();
        List<Delivery> onTheWay = deliveryRepository.findSimulatedOnTheWay();
        Set<Long> active = new HashSet<>();
        for (Delivery delivery : onTheWay) {
            active.add(delivery.getId());
            try {
                drive(delivery);
            } catch (RuntimeException e) {
                log.warn("Simulated courier of delivery {} skipped a tick: {}", delivery.getId(), e.getMessage());
            }
        }
        trips.keySet().retainAll(active);
        stranded.retainAll(active);
    }

    private void pickUpConfirmedOrders() {
        for (Delivery delivery : deliveryRepository.findSimulatedAwaitingPickup(
                clock.instant().minus(properties.pickupDelay()))) {
            try {
                deliveryService.updateDeliveryStatus(delivery.getId(),
                        new UpdateDeliveryStatusRequest(DeliveryStatus.PICKED_UP, null));
                log.info("Simulated courier {} picked up order {}", delivery.getCourierId(), delivery.getOrderId());
            } catch (RuntimeException e) {
                log.warn("Simulated pickup of delivery {} failed: {}", delivery.getId(), e.getMessage());
            }
        }
    }

    private void drive(Delivery delivery) {
        Optional<Courier> courier = courierRepository.findById(delivery.getCourierId());
        if (courier.isEmpty()) {
            return;
        }
        Trip trip = trips.get(delivery.getId());
        if (trip == null) {
            trip = plan(delivery, courier.get());
            if (trip == null) {
                return;
            }
            trips.put(delivery.getId(), trip);
        }
        if (delivery.getStatus() == DeliveryStatus.PICKED_UP) {
            deliveryService.updateDeliveryStatus(delivery.getId(),
                    new UpdateDeliveryStatusRequest(DeliveryStatus.IN_TRANSIT, null));
        }
        Polyline.PathPoint point = trip.advance(properties.tick());
        courierTracking.report(courier.get(), point.position(), point.heading());
        if (trip.arrived()) {
            deliveryService.updateDeliveryStatus(delivery.getId(),
                    new UpdateDeliveryStatusRequest(DeliveryStatus.DELIVERED, null));
            trips.remove(delivery.getId());
            log.info("Simulated courier {} delivered order {}", delivery.getCourierId(), delivery.getOrderId());
        }
    }

    private Trip plan(Delivery delivery, Courier courier) {
        GeoPosition start = delivery.getPickup() != null ? delivery.getPickup() : courier.getPosition();
        if (start == null || delivery.getDropoff() == null) {
            if (stranded.add(delivery.getId())) {
                log.warn("Delivery {} has no route points, simulated courier cannot move", delivery.getId());
            }
            return null;
        }
        TrackedRoute route = routes.plan(delivery.getId(), start, delivery.getDropoff(), properties.metersPerSecond());
        if (delivery.getStatus() == DeliveryStatus.IN_TRANSIT && courier.getPosition() != null) {
            return Trip.resumedAt(route, courier.getPosition());
        }
        return Trip.fromStart(route);
    }
}
