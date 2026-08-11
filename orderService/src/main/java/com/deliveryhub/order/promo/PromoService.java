package com.deliveryhub.order.promo;

import com.deliveryhub.order.exception.ErrorCode;
import com.deliveryhub.order.exception.ItemNotFoundException;
import com.deliveryhub.order.exception.OrderRejectedException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class PromoService {

    private static final Logger log = LoggerFactory.getLogger(PromoService.class);
    private static final String UNIQUE_CODE_CONSTRAINT = "uq_promo_codes_code";

    private final PromoCodeRepository promoCodes;
    private final PromoRedemptionRepository redemptions;
    private final Clock clock;

    public PromoService(PromoCodeRepository promoCodes, PromoRedemptionRepository redemptions, Clock clock) {
        this.promoCodes = promoCodes;
        this.redemptions = redemptions;
        this.clock = clock;
    }

    public PromoUse preview(String code, Long userId, Long restaurantId, BigDecimal subtotal) {
        PromoCode promo = find(code);
        long userUsages = redemptions.countByPromoIdAndUserId(promo.getId(), userId);
        BigDecimal discount = promo.applyTo(restaurantId, subtotal, userUsages, clock.instant());
        return new PromoUse(promo.getId(), promo.getCode(), discount);
    }

    @Transactional
    public PromoUse reserve(String code, Long userId, Long restaurantId, BigDecimal subtotal) {
        PromoUse use = preview(code, userId, restaurantId, subtotal);
        if (promoCodes.reserveUsage(use.promoId()) == 0) {
            throw new OrderRejectedException(ErrorCode.PROMO_LIMIT_REACHED,
                    "Promo code " + use.code() + " is used up");
        }
        PromoCode promo = promoCodes.findById(use.promoId()).orElseThrow();
        promo.requireUserWithinLimit(redemptions.countByPromoIdAndUserId(use.promoId(), userId));
        return use;
    }

    @Transactional
    public void record(PromoUse use, Long userId, Long orderId) {
        redemptions.save(new PromoRedemption(use.promoId(), userId, orderId, use.discount(), clock.instant()));
        log.info("Promo code {} applied to order {} of user {}, discount {}", use.code(), orderId, userId,
                use.discount());
    }

    @Transactional
    public void release(Long orderId) {
        redemptions.findByOrderId(orderId).ifPresent(redemption -> {
            redemptions.delete(redemption);
            promoCodes.releaseUsage(redemption.getPromoId());
            log.info("Promo redemption of order {} released", orderId);
        });
    }

    @Transactional
    public PromoCode create(CreatePromoCodeRequest request) {
        String code = PromoCode.normalize(request.code());
        if (promoCodes.existsByCode(code)) {
            throw codeExists(code);
        }
        PromoCode promo = PromoCode.create(code, request.type(), request.value(), request.minOrderAmount(),
                request.validFrom(), request.validTo(), request.maxRedemptions(), request.maxPerUser(),
                request.restaurantId(), clock.instant());
        PromoCode saved = saveUnique(promo);
        log.info("Promo code {} created", saved.getCode());
        return saved;
    }

    public Page<PromoCode> list(Pageable pageable) {
        return promoCodes.findAll(pageable);
    }

    @Transactional
    public PromoCode deactivate(Long id) {
        PromoCode promo = promoCodes.findById(id).orElseThrow(() -> notFound("Promo code with ID: " + id));
        promo.deactivate();
        log.info("Promo code {} deactivated", promo.getCode());
        return promo;
    }

    private PromoCode find(String code) {
        String normalized = PromoCode.normalize(code);
        return promoCodes.findByCode(normalized)
                .orElseThrow(() -> notFound("Promo code " + normalized));
    }

    private static ItemNotFoundException notFound(String what) {
        return new ItemNotFoundException(ErrorCode.PROMO_NOT_FOUND, what + " was not found");
    }

    private PromoCode saveUnique(PromoCode promo) {
        try {
            return promoCodes.saveAndFlush(promo);
        } catch (DataIntegrityViolationException ex) {
            if (violatesUniqueCode(ex)) {
                throw codeExists(promo.getCode());
            }
            throw ex;
        }
    }

    private static boolean violatesUniqueCode(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(Locale.ROOT).startsWith(UNIQUE_CODE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    private static OrderRejectedException codeExists(String code) {
        return new OrderRejectedException(ErrorCode.PROMO_CODE_EXISTS, "Promo code " + code + " already exists");
    }
}
