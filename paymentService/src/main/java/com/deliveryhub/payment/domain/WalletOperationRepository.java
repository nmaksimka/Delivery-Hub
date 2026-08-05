package com.deliveryhub.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletOperationRepository extends JpaRepository<WalletOperation, Long> {

    Optional<WalletOperation> findByWalletIdAndIdempotencyKey(long walletId, String idempotencyKey);
}
