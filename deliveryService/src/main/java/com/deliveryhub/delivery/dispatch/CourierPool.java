package com.deliveryhub.delivery.dispatch;

import com.deliveryhub.contracts.events.GeoPoint;
import com.deliveryhub.contracts.events.PaymentHeldEvent;
import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.repository.CourierRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
@EnableConfigurationProperties(DispatchProperties.class)
public class CourierPool {

    private final CourierRepository courierRepository;
    private final DeliveryZones deliveryZones;
    private final DispatchProperties properties;

    public CourierPool(CourierRepository courierRepository, DeliveryZones deliveryZones,
                       DispatchProperties properties) {
        this.courierRepository = courierRepository;
        this.deliveryZones = deliveryZones;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Courier> occupyNearest(PaymentHeldEvent event, Instant now) {
        Optional<DeliveryZone> zone = searchZone(event, now);
        Optional<Courier> courier = zone.isPresent()
                ? courierRepository.lockNearestAvailable(zone.get().center().latitude(),
                        zone.get().center().longitude(), zone.get().maxDistanceMeters())
                : courierRepository.lockAnyAvailable();
        courier.ifPresent(found -> found.occupy(now));
        return courier;
    }

    private Optional<DeliveryZone> searchZone(PaymentHeldEvent event, Instant now) {
        Long restaurantId = event.getRestaurantId();
        GeoPoint location = event.getRestaurantLocation();
        if (location == null) {
            return restaurantId == null ? Optional.empty() : deliveryZones.find(restaurantId);
        }
        GeoPosition center = new GeoPosition(location.getLatitude(), location.getLongitude());
        if (restaurantId == null) {
            return Optional.of(new DeliveryZone(center, properties.zoneRadiusMeters()));
        }
        return Optional.of(deliveryZones.remember(restaurantId, center, properties.zoneRadiusMeters(), now));
    }
}
