package com.deliveryhub.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Instant CREATED = Instant.parse("2026-07-10T12:00:00Z");
    private static final Instant LATER = CREATED.plusSeconds(60);

    @Test
    void heldPaymentCanBeCapturedOnce() {
        Payment payment = held();

        assertThat(payment.capture(LATER)).isTrue();
        assertThat(payment.capture(LATER)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void heldPaymentCanBeReleasedOnce() {
        Payment payment = held();

        assertThat(payment.release(LATER)).isTrue();
        assertThat(payment.release(LATER)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.RELEASED);
    }

    @Test
    void capturedPaymentCanBeRefundedOnce() {
        Payment payment = held();
        payment.capture(LATER);

        assertThat(payment.refund(LATER)).isTrue();
        assertThat(payment.refund(LATER)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void heldPaymentCannotBeRefunded() {
        Payment payment = held();

        assertThatThrownBy(() -> payment.refund(LATER))
                .isInstanceOf(PaymentException.class)
                .extracting("code").isEqualTo(ErrorCode.PAYMENT_INVALID_STATE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.HELD);
    }

    @Test
    void releasedPaymentCannotBeCaptured() {
        Payment payment = held();
        payment.release(LATER);

        assertThatThrownBy(() -> payment.capture(LATER))
                .extracting("code").isEqualTo(ErrorCode.PAYMENT_INVALID_STATE);
    }

    @Test
    void capturedPaymentCannotBeReleased() {
        Payment payment = held();
        payment.capture(LATER);

        assertThatThrownBy(() -> payment.release(LATER))
                .extracting("code").isEqualTo(ErrorCode.PAYMENT_INVALID_STATE);
    }

    @Test
    void rejectedPaymentAllowsNoTransitions() {
        Payment payment = Payment.rejected(5L, 7L, new BigDecimal("10.00"), "demo",
                ErrorCode.PAYMENT_INSUFFICIENT_FUNDS, CREATED);

        assertThat(payment.isRejected()).isTrue();
        assertThat(payment.getDeclineReason()).isEqualTo(ErrorCode.PAYMENT_INSUFFICIENT_FUNDS);
        assertThatThrownBy(() -> payment.capture(LATER)).extracting("code").isEqualTo(ErrorCode.PAYMENT_INVALID_STATE);
        assertThatThrownBy(() -> payment.release(LATER)).extracting("code").isEqualTo(ErrorCode.PAYMENT_INVALID_STATE);
        assertThatThrownBy(() -> payment.refund(LATER)).extracting("code").isEqualTo(ErrorCode.PAYMENT_INVALID_STATE);
    }

    @Test
    void repeatedRequestMustHaveSameUserAndAmount() {
        Payment payment = held();

        payment.requireSameRequest(7L, new BigDecimal("250.5"));
        assertThatThrownBy(() -> payment.requireSameRequest(8L, new BigDecimal("250.50")))
                .extracting("code").isEqualTo(ErrorCode.PAYMENT_ORDER_MISMATCH);
        assertThatThrownBy(() -> payment.requireSameRequest(7L, new BigDecimal("250.51")))
                .extracting("code").isEqualTo(ErrorCode.PAYMENT_ORDER_MISMATCH);
    }

    @Test
    void onlyOwnerOrAdminSeesPayment() {
        Payment payment = held();

        assertThat(payment.isVisibleTo(7L, false)).isTrue();
        assertThat(payment.isVisibleTo(8L, false)).isFalse();
        assertThat(payment.isVisibleTo(8L, true)).isTrue();
    }

    @Test
    void amountIsStoredWithTwoDecimals() {
        assertThat(held().getAmount()).isEqualTo(new BigDecimal("250.50"));
        assertThatThrownBy(() -> Payment.held(5L, 7L, new BigDecimal("1.005"), "demo", "ref", CREATED))
                .extracting("code").isEqualTo(ErrorCode.INVALID_AMOUNT);
    }

    private static Payment held() {
        return Payment.held(5L, 7L, new BigDecimal("250.5"), "demo", "demo-order-5", CREATED);
    }
}
