package com.deliveryhub.delivery.service;

import com.deliveryhub.delivery.dto.CourierDto;
import com.deliveryhub.delivery.dto.RegisterCourierRequest;
import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.CourierStatus;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.exception.CourierAlreadyRegisteredException;
import com.deliveryhub.delivery.exception.ForbiddenException;
import com.deliveryhub.delivery.exception.InvalidRequestException;
import com.deliveryhub.delivery.exception.PositionRejectedException;
import com.deliveryhub.delivery.quote.Polyline;
import com.deliveryhub.delivery.repository.CourierRepository;
import com.deliveryhub.delivery.security.CurrentUser;
import com.deliveryhub.delivery.tracking.CourierTracking;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class CourierService {

    private static final Logger log = LoggerFactory.getLogger(CourierService.class);
    private static final Duration MIN_REPORT_INTERVAL = Duration.ofSeconds(1);
    private static final double MAX_SPEED_METERS_PER_SECOND = 150 / 3.6;

    private final CourierRepository courierRepository;
    private final CourierTracking courierTracking;
    private final Clock clock;

    public CourierService(CourierRepository courierRepository, CourierTracking courierTracking, Clock clock) {
        this.courierRepository = courierRepository;
        this.courierTracking = courierTracking;
        this.clock = clock;
    }

    @Transactional
    public CourierDto changeOwnStatus(CurrentUser currentUser, CourierStatus status) {
        Courier courier = courierOf(currentUser);
        Instant now = clock.instant();
        switch (status) {
            case AVAILABLE -> courier.goOnline(now);
            case OFFLINE -> courier.goOffline(now);
            case BUSY -> throw new InvalidRequestException("Courier becomes BUSY only when a delivery is assigned");
        }
        log.info("Courier {} is now {}", courier.getId(), courier.getStatus());
        return CourierDto.of(courier);
    }

    @Transactional
    public void moveOwnPosition(CurrentUser currentUser, GeoPosition position) {
        Courier courier = courierOf(currentUser);
        requirePlausible(courier, position, clock.instant());
        courierTracking.report(courier, position, null);
    }

    private static void requirePlausible(Courier courier, GeoPosition position, Instant now) {
        if (courier.getStatus() == CourierStatus.OFFLINE) {
            throw new PositionRejectedException("Courier is offline, go online before reporting the position");
        }
        GeoPosition previous = courier.getPosition();
        if (previous == null) {
            return;
        }
        Duration sinceLast = Duration.between(courier.getUpdatedAt(), now);
        if (sinceLast.compareTo(MIN_REPORT_INTERVAL) < 0) {
            throw new PositionRejectedException("Position can be reported at most once per second");
        }
        double metersPerSecond = Polyline.meters(previous, position) / (sinceLast.toMillis() / 1000.0);
        if (metersPerSecond > MAX_SPEED_METERS_PER_SECOND) {
            throw new PositionRejectedException("Position jump is faster than 150 km/h");
        }
    }

    public Page<CourierDto> list(CurrentUser currentUser, CourierStatus status, Pageable pageable) {
        requireAdmin(currentUser);
        Page<Courier> couriers = status == null
                ? courierRepository.findAll(pageable)
                : courierRepository.findByStatus(status, pageable);
        return couriers.map(CourierDto::of);
    }

    @Transactional
    public CourierDto register(CurrentUser currentUser, RegisterCourierRequest request) {
        requireAdmin(currentUser);
        Courier courier;
        try {
            courier = courierRepository.saveAndFlush(
                    Courier.register(request.userId(), request.name(), request.phone(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new CourierAlreadyRegisteredException(request.userId());
        }
        log.info("Courier {} registered for user {}", courier.getId(), courier.getUserId());
        return CourierDto.of(courier);
    }

    private Courier courierOf(CurrentUser currentUser) {
        if (!currentUser.isCourier()) {
            throw new ForbiddenException("Only couriers can change their own status and position");
        }
        return courierRepository.findByUserId(currentUser.id())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Courier profile of user " + currentUser.id() + " was not found"));
    }

    private static void requireAdmin(CurrentUser currentUser) {
        if (!currentUser.isAdmin()) {
            throw new ForbiddenException("Only administrators can manage couriers");
        }
    }
}
