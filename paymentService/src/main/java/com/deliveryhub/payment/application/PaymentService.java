package com.deliveryhub.payment.application;

import com.deliveryhub.payment.domain.ErrorCode;
import com.deliveryhub.payment.domain.Money;
import com.deliveryhub.payment.domain.Payment;
import com.deliveryhub.payment.domain.PaymentException;
import com.deliveryhub.payment.domain.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentProvider provider, Clock clock) {
        this.payments = payments;
        this.provider = provider;
        this.clock = clock;
    }

    public Payment open(long orderId, long userId, BigDecimal amount) {
        BigDecimal value = Money.nonNegative(amount);
        Payment payment = payments.findByOrderId(orderId)
                .orElseGet(() -> payments.saveAndFlush(newPayment(orderId, userId, value)));
        payment.requireSameRequest(userId, value);
        return payment;
    }

    @Transactional
    public Payment capture(long orderId) {
        Payment payment = lockedPayment(orderId);
        if (payment.capture(clock.instant()) && !payment.isFree()) {
            approve(provider.capture(ProviderRequest.of(payment)), payment);
            log.info("Payment of order {} captured", orderId);
        }
        return payment;
    }

    @Transactional
    public Payment release(long orderId) {
        Payment payment = lockedPayment(orderId);
        if (payment.release(clock.instant()) && !payment.isFree()) {
            approve(provider.release(ProviderRequest.of(payment)), payment);
            log.info("Payment of order {} released", orderId);
        }
        return payment;
    }

    @Transactional
    public Payment refund(long orderId) {
        Payment payment = lockedPayment(orderId);
        if (payment.refund(clock.instant()) && !payment.isFree()) {
            approve(provider.refund(ProviderRequest.of(payment)), payment);
            log.info("Payment of order {} refunded", orderId);
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment findForViewer(long orderId, CurrentUser viewer) {
        return payments.findByOrderId(orderId)
                .filter(payment -> payment.isVisibleTo(viewer.id(), viewer.isAdmin()))
                .orElseThrow(() -> notFound(orderId));
    }

    private Payment newPayment(long orderId, long userId, BigDecimal amount) {
        if (amount.signum() == 0) {
            log.info("Order {} costs nothing, payment is held without the provider", orderId);
            return Payment.free(orderId, userId, clock.instant());
        }
        ProviderResult result = provider.hold(ProviderRequest.newHold(orderId, userId, amount));
        return switch (result) {
            case ProviderResult.Approved approved -> {
                log.info("Order {} amount {} held by {}", orderId, amount, provider.name());
                yield Payment.held(orderId, userId, amount, provider.name(), approved.reference(), clock.instant());
            }
            case ProviderResult.Declined declined -> {
                log.info("Order {} hold declined by {}: {}", orderId, provider.name(), declined.reason());
                yield Payment.rejected(orderId, userId, amount, provider.name(), declined.reason(), clock.instant());
            }
        };
    }

    private Payment lockedPayment(long orderId) {
        return payments.findForUpdateByOrderId(orderId).orElseThrow(() -> notFound(orderId));
    }

    private static void approve(ProviderResult result, Payment payment) {
        if (result instanceof ProviderResult.Declined declined) {
            throw new PaymentException(ErrorCode.PAYMENT_DECLINED,
                    "Provider declined operation on order " + payment.getOrderId() + ": " + declined.reason());
        }
    }

    private static PaymentException notFound(long orderId) {
        return new PaymentException(ErrorCode.PAYMENT_NOT_FOUND, "Payment of order " + orderId + " not found");
    }
}
