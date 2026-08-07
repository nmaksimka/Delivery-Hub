package com.deliveryhub.delivery.dispatch;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Component
@EnableConfigurationProperties(DispatchProperties.class)
public class CourierPool {

    private static final Set<DeliveryStatus> FINISHED = EnumSet.of(DeliveryStatus.DELIVERED, DeliveryStatus.CANCELLED);

    private final DeliveryRepository deliveryRepository;
    private final DispatchProperties properties;

    public CourierPool(DeliveryRepository deliveryRepository, DispatchProperties properties) {
        this.deliveryRepository = deliveryRepository;
        this.properties = properties;
    }

    public Optional<Long> freeCourier() {
        if (properties.courierIds().isEmpty()) {
            return Optional.empty();
        }
        Set<Long> busy = new HashSet<>(deliveryRepository.findBusyCouriers(properties.courierIds(), FINISHED));
        return properties.courierIds().stream()
                .filter(courierId -> !busy.contains(courierId))
                .findFirst();
    }
}
