package com.deliveryhub.payment.application;

import com.deliveryhub.payment.domain.ErrorCode;
import com.deliveryhub.payment.domain.Money;
import com.deliveryhub.payment.domain.Payment;
import com.deliveryhub.payment.domain.PaymentException;
import com.deliveryhub.payment.domain.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentProvider provider,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.payments = payments;
        this.provider = provider;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Payment hold(long orderId, long userId, BigDecimal amount) {
        Payment payment = holdOrDecline(orderId, userId, amount);
        if (payment.isRejected()) {
            throw new PaymentException(payment.getDeclineReason(), "Payment of order " + orderId + " was declined");
        }
        return payment;
    }

    public Payment holdOrDecline(long orderId, long userId, BigDecimal amount) {
        BigDecimal value = Money.positive(amount);
        Payment payment = findOrHold(orderId, userId, value);
        payment.requireSameRequest(userId, value);
        return payment;
    }

    @Transactional
    public Payment capture(long orderId) {
        Payment payment = lockedPayment(orderId);
        if (payment.capture(clock.instant())) {
            approve(provider.capture(ProviderRequest.of(payment)), payment);
            log.info("Payment of order {} captured", orderId);
        }
        return payment;
    }

    @Transactional
    public Payment release(long orderId) {
        Payment payment = lockedPayment(orderId);
        if (payment.release(clock.instant())) {
            approve(provider.release(ProviderRequest.of(payment)), payment);
            log.info("Payment of order {} released", orderId);
        }
        return payment;
    }

    @Transactional
    public Payment refund(long orderId) {
        Payment payment = lockedPayment(orderId);
        if (payment.refund(clock.instant())) {
            approve(provider.refund(ProviderRequest.of(payment)), payment);
            log.info("Payment of order {} refunded", orderId);
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment findForViewer(long orderId, long viewerId, boolean admin) {
        return payments.findByOrderId(orderId)
                .filter(payment -> payment.isVisibleTo(viewerId, admin))
                .orElseThrow(() -> notFound(orderId));
    }

    private Payment findOrHold(long orderId, long userId, BigDecimal amount) {
        try {
            return transaction.execute(status -> payments.findByOrderId(orderId)
                    .orElseGet(() -> payments.saveAndFlush(newPayment(orderId, userId, amount))));
        } catch (DataIntegrityViolationException ex) {
            log.info("Concurrent hold of order {} lost the race, reading the winner", orderId);
            return payments.findByOrderId(orderId).orElseThrow(() -> ex);
        }
    }

    private Payment newPayment(long orderId, long userId, BigDecimal amount) {
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
