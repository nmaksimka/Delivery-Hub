package com.deliveryhub.order.promo;

import com.deliveryhub.order.exception.Coded;
import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.InvalidPromoException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-09T10:00:00Z");
    private static final long USER = 42L;
    private static final long RESTAURANT = 1L;

    @Mock
    private PromoCodeRepository promoCodes;
    @Mock
    private PromoRedemptionRepository redemptions;

    private PromoService service;
    private PromoCode promo;

    @BeforeEach
    void setUp() {
        service = new PromoService(promoCodes, redemptions, Clock.fixed(NOW, ZoneOffset.UTC));
        promo = PromoCode.create("SPRING10", PromoType.PERCENT, BigDecimal.TEN, null, null, null, 5, 1, null, NOW);
        ReflectionTestUtils.setField(promo, "id", 7L);
    }

    @Test
    void previewLooksCodeUpCaseInsensitivelyAndCalculatesDiscount() {
        when(promoCodes.findByCode("SPRING10")).thenReturn(Optional.of(promo));
        when(redemptions.countByPromoIdAndUserId(7L, USER)).thenReturn(0L);

        PromoUse use = service.preview(" spring10 ", USER, RESTAURANT, new BigDecimal("25.00"));

        assertThat(use).isEqualTo(new PromoUse(7L, "SPRING10", new BigDecimal("2.50")));
    }

    @Test
    void previewOfUnknownCodeIsNotFound() {
        when(promoCodes.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview("nope", USER, RESTAURANT, BigDecimal.TEN))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PROMO_NOT_FOUND));
    }

    @Test
    void reserveFailsWhenAtomicIncrementAffectsNoRows() {
        when(promoCodes.findByCode("SPRING10")).thenReturn(Optional.of(promo));
        when(promoCodes.reserveUsage(7L)).thenReturn(0);

        assertThatThrownBy(() -> service.reserve("SPRING10", USER, RESTAURANT, BigDecimal.TEN))
                .isInstanceOfSatisfying(Coded.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PROMO_LIMIT_REACHED));
    }

    @Test
    void reserveRechecksUserLimitAfterTakingTheSlot() {
        when(promoCodes.findByCode("SPRING10")).thenReturn(Optional.of(promo));
        when(promoCodes.reserveUsage(7L)).thenReturn(1);
        when(promoCodes.findById(7L)).thenReturn(Optional.of(promo));
        when(redemptions.countByPromoIdAndUserId(7L, USER)).thenReturn(0L, 1L);

        assertThatThrownBy(() -> service.reserve("SPRING10", USER, RESTAURANT, BigDecimal.TEN))
                .isInstanceOfSatisfying(Coded.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PROMO_USER_LIMIT_REACHED));
    }

    @Test
    void reserveReturnsUseWhenSlotIsTaken() {
        when(promoCodes.findByCode("SPRING10")).thenReturn(Optional.of(promo));
        when(promoCodes.reserveUsage(7L)).thenReturn(1);
        when(promoCodes.findById(7L)).thenReturn(Optional.of(promo));
        when(redemptions.countByPromoIdAndUserId(7L, USER)).thenReturn(0L);

        PromoUse use = service.reserve("SPRING10", USER, RESTAURANT, new BigDecimal("40.00"));

        assertThat(use.discount()).isEqualTo(new BigDecimal("4.00"));
    }

    @Test
    void recordStoresRedemptionOfOrder() {
        service.record(new PromoUse(7L, "SPRING10", new BigDecimal("2.50")), USER, 15L);

        ArgumentCaptor<PromoRedemption> captor = ArgumentCaptor.forClass(PromoRedemption.class);
        verify(redemptions).save(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(15L);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER);
        assertThat(captor.getValue().getPromoId()).isEqualTo(7L);
        assertThat(captor.getValue().getDiscount()).isEqualByComparingTo("2.50");
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void releaseDeletesRedemptionAndFreesSlot() {
        PromoRedemption redemption = new PromoRedemption(7L, USER, 15L, BigDecimal.ONE, NOW);
        when(redemptions.findByOrderId(15L)).thenReturn(Optional.of(redemption));

        service.release(15L);

        verify(redemptions).delete(redemption);
        verify(promoCodes).releaseUsage(7L);
    }

    @Test
    void releaseOfOrderWithoutRedemptionDoesNothing() {
        when(redemptions.findByOrderId(15L)).thenReturn(Optional.empty());

        service.release(15L);

        verify(promoCodes, never()).releaseUsage(any());
    }

    @Test
    void createRejectsExistingCodeIgnoringCase() {
        when(promoCodes.existsByCode("SPRING10")).thenReturn(true);
        CreatePromoCodeRequest request = new CreatePromoCodeRequest("spring10", PromoType.PERCENT, BigDecimal.TEN,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PROMO_CODE_EXISTS));
        verify(promoCodes, never()).save(any());
    }

    @Test
    void createTurnsUniqueViolationOfConcurrentInsertIntoCodeExists() {
        when(promoCodes.saveAndFlush(any())).thenThrow(violation("uq_promo_codes_code_upper"));
        CreatePromoCodeRequest request = new CreatePromoCodeRequest("race", PromoType.FIXED, BigDecimal.ONE,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(Coded.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PROMO_CODE_EXISTS));
    }

    @Test
    void createRethrowsOtherIntegrityViolations() {
        when(promoCodes.saveAndFlush(any())).thenThrow(violation("ck_promo_codes_used_count"));
        CreatePromoCodeRequest request = new CreatePromoCodeRequest("other", PromoType.FIXED, BigDecimal.ONE,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createValidatesDomainRules() {
        CreatePromoCodeRequest request = new CreatePromoCodeRequest("big", PromoType.PERCENT, new BigDecimal("150"),
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(InvalidPromoException.class);
    }

    @Test
    void deactivateTurnsOffActiveFlag() {
        when(promoCodes.findById(7L)).thenReturn(Optional.of(promo));

        service.deactivate(7L);

        assertThat(promo.isActive()).isFalse();
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"), constraint));
    }
}
