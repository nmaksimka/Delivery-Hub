package com.deliveryhub.delivery.tracking;

import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.repository.CourierRepository;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import com.deliveryhub.delivery.security.CurrentUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class TrackingAccess {

    private final DeliveryRepository deliveryRepository;
    private final CourierRepository courierRepository;

    public TrackingAccess(DeliveryRepository deliveryRepository, CourierRepository courierRepository) {
        this.deliveryRepository = deliveryRepository;
        this.courierRepository = courierRepository;
    }

    @Transactional(readOnly = true)
    public boolean canWatch(CurrentUser user, long orderId) {
        if (user.isAdmin()) {
            return true;
        }
        Optional<Delivery> delivery = deliveryRepository.findByOrderId(orderId);
        if (delivery.isEmpty()) {
            return false;
        }
        if (user.isCourier()) {
            return courierRepository.findByUserId(user.id())
                    .map(Courier::getId)
                    .filter(courierId -> courierId.equals(delivery.get().getCourierId()))
                    .isPresent();
        }
        return delivery.get().belongsTo(user.id());
    }
}
