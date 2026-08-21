package com.deliveryhub.delivery.tracking;

import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.quote.RoutePath;
import com.deliveryhub.delivery.quote.RoutePlanner;
import com.deliveryhub.delivery.quote.RoutingProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeliveryRoutes {

    private final RoutePlanner routePlanner;
    private final RoutingProperties routing;
    private final Map<Long, TrackedRoute> routes = new ConcurrentHashMap<>();

    public DeliveryRoutes(RoutePlanner routePlanner, RoutingProperties routing) {
        this.routePlanner = routePlanner;
        this.routing = routing;
    }

    public Optional<TrackedRoute> known(long deliveryId) {
        return Optional.ofNullable(routes.get(deliveryId));
    }

    public Optional<TrackedRoute> of(Delivery delivery) {
        TrackedRoute known = routes.get(delivery.getId());
        if (known != null) {
            return Optional.of(known);
        }
        if (delivery.getPickup() == null || delivery.getDropoff() == null) {
            return Optional.empty();
        }
        RoutePath path = routePlanner.path(delivery.getPickup(), delivery.getDropoff());
        double speed = path.metersPerSecond() > 0 ? path.metersPerSecond() : routing.fallbackSpeedKmh() / 3.6;
        return Optional.of(remember(delivery.getId(), new TrackedRoute(path, speed)));
    }

    public TrackedRoute plan(long deliveryId, GeoPosition from, GeoPosition to, double metersPerSecond) {
        return remember(deliveryId, new TrackedRoute(routePlanner.path(from, to), metersPerSecond));
    }

    private TrackedRoute remember(long deliveryId, TrackedRoute route) {
        routes.put(deliveryId, route);
        return route;
    }

    @EventListener
    public void forgetFinished(TrackingUpdate update) {
        if (update.finishesDelivery()) {
            routes.remove(update.deliveryId());
        }
    }
}
