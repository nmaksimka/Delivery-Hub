package com.deliveryhub.order.promo;

import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.InvalidPromoException;
import com.deliveryhub.order.exception.OrderRejectedException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Entity
@Table(name = "promo_codes")
@DynamicUpdate
public class PromoCode {

    private static final Pattern CODE_FORMAT = Pattern.compile("[A-Z0-9_-]{3,32}");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private PromoType type;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal value;

    @Column(name = "min_order_amount", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal minOrderAmount;

    @Column(name = "valid_from", updatable = false)
    private Instant validFrom;

    @Column(name = "valid_to", updatable = false)
    private Instant validTo;

    @Column(name = "max_redemptions", updatable = false)
    private Integer maxRedemptions;

    @Column(name = "max_per_user", updatable = false)
    private Integer maxPerUser;

    @Column(name = "restaurant_id", updatable = false)
    private Long restaurantId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "used_count", nullable = false, insertable = false, updatable = false)
    private int usedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PromoCode() {
    }

    private PromoCode(String code, PromoType type, BigDecimal value, BigDecimal minOrderAmount, Instant validFrom,
                      Instant validTo, Integer maxRedemptions, Integer maxPerUser, Long restaurantId,
                      Instant createdAt) {
        this.code = code;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.maxRedemptions = maxRedemptions;
        this.maxPerUser = maxPerUser;
        this.restaurantId = restaurantId;
        this.active = true;
        this.createdAt = createdAt;
    }

    public static PromoCode create(String code, PromoType type, BigDecimal value, BigDecimal minOrderAmount,
                                   Instant validFrom, Instant validTo, Integer maxRedemptions, Integer maxPerUser,
                                   Long restaurantId, Instant createdAt) {
        String normalized = normalize(code);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(createdAt, "createdAt");
        if (!CODE_FORMAT.matcher(normalized).matches()) {
            throw new InvalidPromoException("Promo code must contain 3-32 latin letters, digits, '-' or '_'");
        }
        BigDecimal scaledValue = value.setScale(2, RoundingMode.HALF_UP);
        if (scaledValue.signum() <= 0) {
            throw new InvalidPromoException("Discount value must be positive");
        }
        if (type == PromoType.PERCENT && scaledValue.compareTo(HUNDRED) > 0) {
            throw new InvalidPromoException("Percent discount must not exceed 100");
        }
        BigDecimal minimum = minOrderAmount == null
                ? BigDecimal.ZERO.setScale(2)
                : minOrderAmount.setScale(2, RoundingMode.HALF_UP);
        if (minimum.signum() < 0) {
            throw new InvalidPromoException("Minimum order amount must not be negative");
        }
        if (validFrom != null && validTo != null && !validTo.isAfter(validFrom)) {
            throw new InvalidPromoException("validTo must be later than validFrom");
        }
        requirePositive(maxRedemptions, "maxRedemptions");
        requirePositive(maxPerUser, "maxPerUser");
        return new PromoCode(normalized, type, scaledValue, minimum, validFrom, validTo, maxRedemptions, maxPerUser,
                restaurantId, createdAt);
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    public BigDecimal applyTo(Long orderRestaurantId, BigDecimal subtotal, long userUsages, Instant now) {
        Objects.requireNonNull(subtotal, "subtotal");
        Objects.requireNonNull(now, "now");
        if (!active) {
            throw rejected(ErrorCode.PROMO_INACTIVE, "Promo code " + code + " is not active");
        }
        if (validFrom != null && now.isBefore(validFrom)) {
            throw rejected(ErrorCode.PROMO_NOT_STARTED, "Promo code " + code + " is not valid yet");
        }
        if (validTo != null && now.isAfter(validTo)) {
            throw rejected(ErrorCode.PROMO_EXPIRED, "Promo code " + code + " has expired");
        }
        if (restaurantId != null && !restaurantId.equals(orderRestaurantId)) {
            throw rejected(ErrorCode.PROMO_OTHER_RESTAURANT,
                    "Promo code " + code + " is valid only for restaurant " + restaurantId);
        }
        if (subtotal.compareTo(minOrderAmount) < 0) {
            throw rejected(ErrorCode.PROMO_MIN_AMOUNT,
                    "Promo code " + code + " requires an order of at least " + minOrderAmount);
        }
        if (maxRedemptions != null && usedCount >= maxRedemptions) {
            throw rejected(ErrorCode.PROMO_LIMIT_REACHED, "Promo code " + code + " is used up");
        }
        requireUserWithinLimit(userUsages);
        return discountFor(subtotal);
    }

    public void requireUserWithinLimit(long userUsages) {
        if (maxPerUser != null && userUsages >= maxPerUser) {
            throw rejected(ErrorCode.PROMO_USER_LIMIT_REACHED,
                    "Promo code " + code + " can be used only " + maxPerUser + " time(s) per user");
        }
    }

    public void deactivate() {
        active = false;
    }

    private BigDecimal discountFor(BigDecimal subtotal) {
        BigDecimal raw = type == PromoType.PERCENT
                ? subtotal.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : value;
        return raw.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    private static OrderRejectedException rejected(ErrorCode errorCode, String message) {
        return new OrderRejectedException(errorCode, message);
    }

    private static void requirePositive(Integer limit, String name) {
        if (limit != null && limit <= 0) {
            throw new InvalidPromoException(name + " must be positive");
        }
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public PromoType getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public Integer getMaxRedemptions() {
        return maxRedemptions;
    }

    public Integer getMaxPerUser() {
        return maxPerUser;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public boolean isActive() {
        return active;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
