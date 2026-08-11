package com.deliveryhub.payment.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> findForUpdateByUserId(long userId);

    @Modifying
    @Query(value = """
            INSERT INTO wallets (user_id, available, held, version, created_at, updated_at)
            VALUES (:userId, 0, 0, 0, :now, :now)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int openIfAbsent(long userId, Instant now);
}
