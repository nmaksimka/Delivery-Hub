package com.deliveryhub.payment.infrastructure.demo;

import com.deliveryhub.payment.application.OnDemoProvider;
import com.deliveryhub.payment.application.PaymentProvider;
import com.deliveryhub.payment.application.ProviderRequest;
import com.deliveryhub.payment.application.ProviderResult;
import com.deliveryhub.payment.application.WalletService;
import com.deliveryhub.payment.domain.ErrorCode;
import com.deliveryhub.payment.domain.Wallet;
import com.deliveryhub.payment.domain.WalletOperation;
import com.deliveryhub.payment.domain.WalletOperationRepository;
import com.deliveryhub.payment.domain.WalletOperationType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
@OnDemoProvider
@Transactional(propagation = Propagation.MANDATORY)
public class DemoPaymentProvider implements PaymentProvider {

    static final String NAME = "demo";
    private static final String REFERENCE_PREFIX = "demo-order-";

    private final WalletService wallets;
    private final WalletOperationRepository operations;
    private final Clock clock;

    public DemoPaymentProvider(WalletService wallets, WalletOperationRepository operations, Clock clock) {
        this.wallets = wallets;
        this.operations = operations;
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ProviderResult hold(ProviderRequest request) {
        Wallet wallet = wallets.locked(request.userId());
        if (!wallet.canHold(request.amount())) {
            return new ProviderResult.Declined(ErrorCode.PAYMENT_INSUFFICIENT_FUNDS);
        }
        Instant now = clock.instant();
        wallet.hold(request.amount(), now);
        record(wallet, WalletOperationType.HOLD, request, now);
        return new ProviderResult.Approved(REFERENCE_PREFIX + request.orderId());
    }

    @Override
    public ProviderResult capture(ProviderRequest request) {
        Wallet wallet = wallets.locked(request.userId());
        Instant now = clock.instant();
        wallet.captureHeld(request.amount(), now);
        record(wallet, WalletOperationType.CAPTURE, request, now);
        return new ProviderResult.Approved(request.reference());
    }

    @Override
    public ProviderResult release(ProviderRequest request) {
        Wallet wallet = wallets.locked(request.userId());
        Instant now = clock.instant();
        wallet.releaseHeld(request.amount(), now);
        record(wallet, WalletOperationType.RELEASE, request, now);
        return new ProviderResult.Approved(request.reference());
    }

    @Override
    public ProviderResult refund(ProviderRequest request) {
        Wallet wallet = wallets.locked(request.userId());
        Instant now = clock.instant();
        wallet.refund(request.amount(), now);
        record(wallet, WalletOperationType.REFUND, request, now);
        return new ProviderResult.Approved(request.reference());
    }

    private void record(Wallet wallet, WalletOperationType type, ProviderRequest request, Instant now) {
        operations.save(WalletOperation.forOrder(wallet, type, request.orderId(), request.amount(), now));
    }
}
