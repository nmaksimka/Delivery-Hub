package com.deliveryhub.delivery.service;

import com.deliveryhub.delivery.dto.CourierDto;
import com.deliveryhub.delivery.dto.RegisterCourierRequest;
import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.CourierStatus;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.exception.CourierAlreadyRegisteredException;
import com.deliveryhub.delivery.exception.ForbiddenException;
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
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class CourierService {

    private static final Logger log = LoggerFactory.getLogger(CourierService.class);

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
            case BUSY -> throw new IllegalArgumentException("Courier becomes BUSY only when a delivery is assigned");
        }
        log.info("Courier {} is now {}", courier.getId(), courier.getStatus());
        return CourierDto.of(courier);
    }

    @Transactional
    public void moveOwnPosition(CurrentUser currentUser, GeoPosition position) {
        courierTracking.report(courierOf(currentUser), position, null);
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
