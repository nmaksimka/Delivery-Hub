package com.deliveryhub.payment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final BigDecimal LIMIT = new BigDecimal("1000.00");

    @Test
    void newWalletIsEmpty() {
        Wallet wallet = Wallet.openFor(7L, NOW);

        assertThat(wallet.getAvailable()).isEqualByComparingTo("0");
        assertThat(wallet.getHeld()).isEqualByComparingTo("0");
    }

    @Test
    void topUpAddsMoneyKeepingTwoDecimals() {
        Wallet wallet = Wallet.openFor(7L, NOW);

        wallet.topUp(new BigDecimal("100.5"), LIMIT, NOW);

        assertThat(wallet.getAvailable()).isEqualTo(new BigDecimal("100.50"));
    }

    @Test
    void topUpCannotExceedLimitCountingHeldMoney() {
        Wallet wallet = walletWith("900.00");
        wallet.hold(new BigDecimal("400.00"), NOW);

        assertThatThrownBy(() -> wallet.topUp(new BigDecimal("100.01"), LIMIT, NOW))
                .isInstanceOf(PaymentException.class)
                .extracting("code").isEqualTo(ErrorCode.WALLET_LIMIT_EXCEEDED);
        wallet.topUp(new BigDecimal("100.00"), LIMIT, NOW);
        assertThat(wallet.getAvailable()).isEqualByComparingTo("600.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "10000000000.00"})
    void rejectsInvalidAmounts(String amount) {
        Wallet wallet = Wallet.openFor(7L, NOW);

        assertThatThrownBy(() -> wallet.topUp(new BigDecimal(amount), new BigDecimal("99999999999"), NOW))
                .isInstanceOf(PaymentException.class)
                .extracting("code").isEqualTo(ErrorCode.INVALID_AMOUNT);
    }

    @Test
    void holdMovesMoneyFromAvailableToHeld() {
        Wallet wallet = walletWith("500.00");

        wallet.hold(new BigDecimal("120.30"), NOW);

        assertThat(wallet.getAvailable()).isEqualByComparingTo("379.70");
        assertThat(wallet.getHeld()).isEqualByComparingTo("120.30");
    }

    @Test
    void holdOfMoreThanAvailableIsRejectedAndChangesNothing() {
        Wallet wallet = walletWith("100.00");

        assertThat(wallet.canHold(new BigDecimal("100.01"))).isFalse();
        assertThatThrownBy(() -> wallet.hold(new BigDecimal("100.01"), NOW))
                .isInstanceOf(PaymentException.class)
                .extracting("code").isEqualTo(ErrorCode.PAYMENT_INSUFFICIENT_FUNDS);
        assertThat(wallet.getAvailable()).isEqualByComparingTo("100.00");
        assertThat(wallet.getHeld()).isEqualByComparingTo("0");
    }

    @Test
    void holdOfWholeBalanceIsAllowed() {
        Wallet wallet = walletWith("100.00");

        wallet.hold(new BigDecimal("100.00"), NOW);

        assertThat(wallet.getAvailable()).isEqualByComparingTo("0");
    }

    @Test
    void captureTakesHeldMoneyAway() {
        Wallet wallet = walletWith("500.00");
        wallet.hold(new BigDecimal("200.00"), NOW);

        wallet.captureHeld(new BigDecimal("200.00"), NOW);

        assertThat(wallet.getAvailable()).isEqualByComparingTo("300.00");
        assertThat(wallet.getHeld()).isEqualByComparingTo("0");
    }

    @Test
    void releaseReturnsHeldMoneyToAvailable() {
        Wallet wallet = walletWith("500.00");
        wallet.hold(new BigDecimal("200.00"), NOW);

        wallet.releaseHeld(new BigDecimal("200.00"), NOW);

        assertThat(wallet.getAvailable()).isEqualByComparingTo("500.00");
        assertThat(wallet.getHeld()).isEqualByComparingTo("0");
    }

    @Test
    void cannotCaptureOrReleaseMoreThanHeld() {
        Wallet wallet = walletWith("500.00");
        wallet.hold(new BigDecimal("50.00"), NOW);

        assertThatThrownBy(() -> wallet.captureHeld(new BigDecimal("50.01"), NOW))
                .extracting("code").isEqualTo(ErrorCode.PAYMENT_INVALID_STATE);
        assertThatThrownBy(() -> wallet.releaseHeld(new BigDecimal("50.01"), NOW))
                .extracting("code").isEqualTo(ErrorCode.PAYMENT_INVALID_STATE);
        assertThat(wallet.getHeld()).isEqualByComparingTo("50.00");
    }

    @Test
    void refundAddsMoneyBack() {
        Wallet wallet = walletWith("500.00");
        wallet.hold(new BigDecimal("200.00"), NOW);
        wallet.captureHeld(new BigDecimal("200.00"), NOW);

        wallet.refund(new BigDecimal("200.00"), NOW);

        assertThat(wallet.getAvailable()).isEqualByComparingTo("500.00");
    }

    private static Wallet walletWith(String amount) {
        Wallet wallet = Wallet.openFor(7L, NOW);
        wallet.topUp(new BigDecimal(amount), LIMIT, NOW);
        return wallet;
    }
}
