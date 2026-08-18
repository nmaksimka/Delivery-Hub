package com.deliveryhub.delivery.tracking;

import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.quote.Polyline;
import com.deliveryhub.delivery.repository.CourierRepository;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class CourierTracking {

    private final CourierRepository courierRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryRoutes routes;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CourierTracking(CourierRepository courierRepository, DeliveryRepository deliveryRepository,
                           DeliveryRoutes routes, ApplicationEventPublisher events, Clock clock) {
        this.courierRepository = courierRepository;
        this.deliveryRepository = deliveryRepository;
        this.routes = routes;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void report(Courier courier, GeoPosition position, Double heading) {
        GeoPosition previous = courier.getPosition();
        Instant now = clock.instant();
        courierRepository.moveTo(courier.getId(), position.latitude(), position.longitude(), now);
        deliveryRepository.findActiveByCourierId(courier.getId()).ifPresent(delivery -> {
            double course = heading != null ? heading : headingFrom(previous, position);
            Eta eta = delivery.isOnTheWay()
                    ? routes.of(delivery).map(route -> route.etaFrom(position)).orElse(null)
                    : null;
            events.publishEvent(TrackingUpdate.position(delivery, position, course, eta, now));
        });
    }

    private static double headingFrom(GeoPosition previous, GeoPosition position) {
        return previous == null ? 0 : Polyline.heading(previous, position);
    }
}
