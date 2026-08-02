package com.deliveryhub.order.promo;

import com.deliveryhub.order.exception.Coded;
import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.InvalidPromoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromoCodeTest {

    private static final Instant NOW = Instant.parse("2026-07-09T10:00:00Z");
    private static final Long RESTAURANT = 1L;

    private static PromoCode percent(String value) {
        return PromoCode.create("spring10", PromoType.PERCENT, new BigDecimal(value), null, null, null, null, null,
                null, NOW);
    }

    private static PromoCode fixed(String value, String minOrder) {
        return PromoCode.create("minus5", PromoType.FIXED, new BigDecimal(value), new BigDecimal(minOrder), null, null,
                null, null, null, NOW);
    }

    private static PromoCode limited(Integer maxRedemptions, Integer maxPerUser, int used) {
        PromoCode promo = PromoCode.create("limited", PromoType.FIXED, BigDecimal.ONE, null, null, null,
                maxRedemptions, maxPerUser, null, NOW);
        ReflectionTestUtils.setField(promo, "usedCount", used);
        return promo;
    }

    private static ErrorCode codeOf(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            return ((Coded) e).code();
        }
        return null;
    }

    @Test
    void normalizesCodeToUpperCase() {
        assertThat(percent("10").getCode()).isEqualTo("SPRING10");
        assertThat(PromoCode.normalize("  spring10 ")).isEqualTo("SPRING10");
    }

    @ParameterizedTest
    @CsvSource({
            "10,   100.00, 10.00",
            "15,   33.33,  5.00",
            "12.5, 10.00,  1.25",
            "33,   0.05,   0.02",
            "33,   0.01,   0.00",
            "100,  47.30,  47.30"
    })
    void percentDiscountIsRoundedHalfUpToCents(String percent, String subtotal, String expected) {
        BigDecimal discount = percent(percent).applyTo(RESTAURANT, new BigDecimal(subtotal), 0, NOW);

        assertThat(discount).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void fixedDiscountNeverExceedsSubtotal() {
        PromoCode promo = fixed("5.00", "0");

        assertThat(promo.applyTo(RESTAURANT, new BigDecimal("3.20"), 0, NOW)).isEqualTo(new BigDecimal("3.20"));
        assertThat(promo.applyTo(RESTAURANT, new BigDecimal("20.00"), 0, NOW)).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    void minimumOrderAmountBoundaryIsInclusive() {
        PromoCode promo = fixed("5.00", "30.00");

        assertThat(promo.applyTo(RESTAURANT, new BigDecimal("30.00"), 0, NOW)).isEqualTo(new BigDecimal("5.00"));
        assertThat(codeOf(() -> promo.applyTo(RESTAURANT, new BigDecimal("29.99"), 0, NOW)))
                .isEqualTo(ErrorCode.PROMO_MIN_AMOUNT);
    }

    @Test
    void validityWindowBoundariesAreInclusive() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-31T23:59:59Z");
        PromoCode promo = PromoCode.create("JULY", PromoType.FIXED, BigDecimal.ONE, null, from, to, null, null, null,
                NOW);
        BigDecimal subtotal = BigDecimal.TEN;

        assertThat(promo.applyTo(RESTAURANT, subtotal, 0, from)).isEqualTo(new BigDecimal("1.00"));
        assertThat(promo.applyTo(RESTAURANT, subtotal, 0, to)).isEqualTo(new BigDecimal("1.00"));
        assertThat(codeOf(() -> promo.applyTo(RESTAURANT, subtotal, 0, from.minusSeconds(1))))
                .isEqualTo(ErrorCode.PROMO_NOT_STARTED);
        assertThat(codeOf(() -> promo.applyTo(RESTAURANT, subtotal, 0, to.plusSeconds(1))))
                .isEqualTo(ErrorCode.PROMO_EXPIRED);
    }

    @Test
    void totalLimitIsReachedWhenUsedCountEqualsMaximum() {
        BigDecimal subtotal = BigDecimal.TEN;

        assertThat(limited(3, null, 2).applyTo(RESTAURANT, subtotal, 0, NOW)).isEqualTo(new BigDecimal("1.00"));
        assertThat(codeOf(() -> limited(3, null, 3).applyTo(RESTAURANT, subtotal, 0, NOW)))
                .isEqualTo(ErrorCode.PROMO_LIMIT_REACHED);
    }

    @Test
    void perUserLimitIsReachedWhenUserUsagesEqualMaximum() {
        PromoCode promo = limited(null, 2, 10);
        BigDecimal subtotal = BigDecimal.TEN;

        assertThat(promo.applyTo(RESTAURANT, subtotal, 1, NOW)).isEqualTo(new BigDecimal("1.00"));
        assertThat(codeOf(() -> promo.applyTo(RESTAURANT, subtotal, 2, NOW)))
                .isEqualTo(ErrorCode.PROMO_USER_LIMIT_REACHED);
    }

    @Test
    void restrictedToRestaurantRejectsOthers() {
        PromoCode promo = PromoCode.create("ONLYONE", PromoType.FIXED, BigDecimal.ONE, null, null, null, null, null,
                7L, NOW);

        assertThat(promo.applyTo(7L, BigDecimal.TEN, 0, NOW)).isEqualTo(new BigDecimal("1.00"));
        assertThat(codeOf(() -> promo.applyTo(8L, BigDecimal.TEN, 0, NOW)))
                .isEqualTo(ErrorCode.PROMO_OTHER_RESTAURANT);
    }

    @Test
    void deactivatedPromoIsRejected() {
        PromoCode promo = percent("10");
        promo.deactivate();

        assertThat(codeOf(() -> promo.applyTo(RESTAURANT, BigDecimal.TEN, 0, NOW)))
                .isEqualTo(ErrorCode.PROMO_INACTIVE);
    }

    @ParameterizedTest
    @CsvSource({
            "PERCENT, 0",
            "PERCENT, -5",
            "PERCENT, 100.01",
            "FIXED,   0",
            "FIXED,   -1"
    })
    void rejectsIncorrectDiscountValue(PromoType type, String value) {
        assertThatThrownBy(() -> PromoCode.create("CODE1", type, new BigDecimal(value), null, null, null, null, null,
                null, NOW)).isInstanceOf(InvalidPromoException.class);
    }

    @Test
    void rejectsBadCodeFormatInvertedWindowAndNonPositiveLimits() {
        assertThatThrownBy(() -> PromoCode.create("a b", PromoType.FIXED, BigDecimal.ONE, null, null, null, null, null,
                null, NOW)).isInstanceOf(InvalidPromoException.class);
        assertThatThrownBy(() -> PromoCode.create("ab", PromoType.FIXED, BigDecimal.ONE, null, null, null, null, null,
                null, NOW)).isInstanceOf(InvalidPromoException.class);
        assertThatThrownBy(() -> PromoCode.create("CODE1", PromoType.FIXED, BigDecimal.ONE, null, NOW, NOW, null, null,
                null, NOW)).isInstanceOf(InvalidPromoException.class);
        assertThatThrownBy(() -> PromoCode.create("CODE1", PromoType.FIXED, BigDecimal.ONE, null, null, null, 0, null,
                null, NOW)).isInstanceOf(InvalidPromoException.class);
        assertThatThrownBy(() -> PromoCode.create("CODE1", PromoType.FIXED, BigDecimal.ONE, null, null, null, null, -1,
                null, NOW)).isInstanceOf(InvalidPromoException.class);
    }
}
