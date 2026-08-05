package com.deliveryhub.payment.application;

import com.deliveryhub.payment.domain.ErrorCode;
import com.deliveryhub.payment.domain.Money;
import com.deliveryhub.payment.domain.PaymentException;
import com.deliveryhub.payment.domain.Wallet;
import com.deliveryhub.payment.domain.WalletOperation;
import com.deliveryhub.payment.domain.WalletOperationRepository;
import com.deliveryhub.payment.domain.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@OnDemoProvider
public class WalletService {

    private final WalletRepository wallets;
    private final WalletOperationRepository operations;
    private final PaymentProperties properties;
    private final Clock clock;

    public WalletService(WalletRepository wallets, WalletOperationRepository operations,
                         PaymentProperties properties, Clock clock) {
        this.wallets = wallets;
        this.operations = operations;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Wallet current(long userId) {
        return wallets.findByUserId(userId).orElseGet(() -> Wallet.openFor(userId, clock.instant()));
    }

    @Transactional
    public TopUp topUp(long userId, String idempotencyKey, BigDecimal amount) {
        BigDecimal value = Money.positive(amount);
        Wallet wallet = locked(userId);
        Optional<WalletOperation> previous = operations.findByWalletIdAndIdempotencyKey(wallet.getId(), idempotencyKey);
        if (previous.isPresent()) {
            if (!previous.get().hasSameAmount(value)) {
                throw new PaymentException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "Idempotency-Key was already used for a top-up with another amount");
            }
            return new TopUp(wallet, true);
        }
        Instant now = clock.instant();
        wallet.topUp(value, properties.demo().maxBalance(), now);
        operations.save(WalletOperation.topUp(wallet, value, idempotencyKey, now));
        return new TopUp(wallet, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Wallet locked(long userId) {
        wallets.openIfAbsent(userId, clock.instant());
        return wallets.findForUpdateByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Wallet of user " + userId + " was not opened"));
    }

    public record TopUp(Wallet wallet, boolean replayed) {
    }
}
