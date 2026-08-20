package com.deliveryhub.delivery.service;

import com.deliveryhub.delivery.dto.CourierDto;
import com.deliveryhub.delivery.dto.RegisterCourierRequest;
import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.CourierStatus;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.exception.CourierAlreadyRegisteredException;
import com.deliveryhub.delivery.exception.CourierBusyException;
import com.deliveryhub.delivery.exception.InvalidRequestException;
import com.deliveryhub.delivery.exception.ForbiddenException;
import com.deliveryhub.delivery.repository.CourierRepository;
import com.deliveryhub.delivery.security.CurrentUser;
import com.deliveryhub.delivery.tracking.CourierTracking;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    private static final CurrentUser COURIER = new CurrentUser(501L, "COURIER");
    private static final CurrentUser ADMIN = new CurrentUser(1L, "ADMIN");

    @Mock
    private CourierRepository courierRepository;
    @Mock
    private CourierTracking courierTracking;

    private CourierService courierService;

    @BeforeEach
    void setUp() {
        courierService = new CourierService(courierRepository, courierTracking, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void courierGoesOnline() {
        Courier courier = Courier.register(501L, "Алексей", "+79990000001", NOW);
        when(courierRepository.findByUserId(501L)).thenReturn(Optional.of(courier));

        CourierDto dto = courierService.changeOwnStatus(COURIER, CourierStatus.AVAILABLE);

        assertThat(dto.status()).isEqualTo(CourierStatus.AVAILABLE);
    }

    @Test
    void courierCannotMarkHimselfBusy() {
        when(courierRepository.findByUserId(501L))
                .thenReturn(Optional.of(Courier.register(501L, "Алексей", "+79990000001", NOW)));

        assertThatThrownBy(() -> courierService.changeOwnStatus(COURIER, CourierStatus.BUSY))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void busyCourierCannotGoOffline() {
        Courier courier = Courier.register(501L, "Алексей", "+79990000001", NOW);
        courier.occupy(NOW);
        when(courierRepository.findByUserId(501L)).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> courierService.changeOwnStatus(COURIER, CourierStatus.OFFLINE))
                .isInstanceOf(CourierBusyException.class);
    }

    @Test
    void userWithoutCourierProfileGetsNotFound() {
        when(courierRepository.findByUserId(501L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierService.moveOwnPosition(COURIER, new GeoPosition(56.48, 84.95)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void customerCannotActAsCourier() {
        assertThatThrownBy(() -> courierService.changeOwnStatus(new CurrentUser(7L, "USER"), CourierStatus.AVAILABLE))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(courierRepository);
    }

    @Test
    void onlyAdminListsCouriers() {
        assertThatThrownBy(() -> courierService.list(COURIER, null, Pageable.unpaged()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void secondProfileForSameUserIsConflict() {
        when(courierRepository.saveAndFlush(any(Courier.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> courierService.register(ADMIN, new RegisterCourierRequest(501L, "Алексей", "+7999")))
                .isInstanceOf(CourierAlreadyRegisteredException.class);
        verify(courierRepository).saveAndFlush(any(Courier.class));
    }
}
