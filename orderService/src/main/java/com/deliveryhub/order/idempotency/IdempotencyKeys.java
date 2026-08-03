package com.deliveryhub.order.idempotency;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component
public class IdempotencyKeys {

    private static final int CLAIM_ATTEMPTS = 3;
    private static final String IN_PROGRESS = "IN_PROGRESS";

    private static final String CLAIM = """
            INSERT INTO idempotency_keys (user_id, method, path, key, request_hash, status, created_at, expires_at)
            VALUES (:userId, :method, :path, :key, :requestHash, 'IN_PROGRESS', :now, :expiresAt)
            ON CONFLICT (user_id, method, path, key) DO UPDATE
            SET request_hash = EXCLUDED.request_hash, status = 'IN_PROGRESS', response_status = NULL,
                response_body = NULL, created_at = EXCLUDED.created_at, expires_at = EXCLUDED.expires_at
            WHERE idempotency_keys.expires_at <= EXCLUDED.created_at
            """;
    private static final String FIND = """
            SELECT request_hash, status, response_status, response_body::text AS response_body
            FROM idempotency_keys
            WHERE user_id = :userId AND method = :method AND path = :path AND key = :key
            """;
    private static final String COMPLETE = """
            UPDATE idempotency_keys
            SET status = 'COMPLETED', response_status = :responseStatus, response_body = CAST(:responseBody AS jsonb)
            WHERE user_id = :userId AND method = :method AND path = :path AND key = :key AND status = 'IN_PROGRESS'
            """;
    private static final String RELEASE = """
            DELETE FROM idempotency_keys
            WHERE user_id = :userId AND method = :method AND path = :path AND key = :key AND status = 'IN_PROGRESS'
            """;

    private final JdbcClient jdbcClient;
    private final IdempotencyProperties properties;
    private final Clock clock;

    public IdempotencyKeys(JdbcClient jdbcClient, IdempotencyProperties properties, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.properties = properties;
        this.clock = clock;
    }

    public IdempotencyClaim claim(IdempotencyScope scope, String requestHash) {
        for (int attempt = 0; attempt < CLAIM_ATTEMPTS; attempt++) {
            if (insert(scope, requestHash)) {
                return new IdempotencyClaim.Acquired();
            }
            Optional<StoredKey> stored = find(scope);
            if (stored.isPresent()) {
                return stored.get().claimFor(requestHash);
            }
        }
        return new IdempotencyClaim.InProgress();
    }

    public void complete(IdempotencyScope scope, int responseStatus, String responseBody) {
        bind(jdbcClient.sql(COMPLETE), scope)
                .param("responseStatus", responseStatus)
                .param("responseBody", responseBody)
                .update();
    }

    public void release(IdempotencyScope scope) {
        bind(jdbcClient.sql(RELEASE), scope).update();
    }

    public int deleteExpired(Instant now) {
        return jdbcClient.sql("DELETE FROM idempotency_keys WHERE expires_at <= :now")
                .param("now", Timestamp.from(now))
                .update();
    }

    private boolean insert(IdempotencyScope scope, String requestHash) {
        Instant now = clock.instant();
        return bind(jdbcClient.sql(CLAIM), scope)
                .param("requestHash", requestHash)
                .param("now", Timestamp.from(now))
                .param("expiresAt", Timestamp.from(now.plus(properties.ttl())))
                .update() == 1;
    }

    private Optional<StoredKey> find(IdempotencyScope scope) {
        return bind(jdbcClient.sql(FIND), scope)
                .query((rs, rowNum) -> new StoredKey(
                        rs.getString("request_hash"),
                        rs.getString("status"),
                        rs.getInt("response_status"),
                        rs.getString("response_body")))
                .optional();
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, IdempotencyScope scope) {
        return statement
                .param("userId", scope.userId())
                .param("method", scope.method())
                .param("path", scope.path())
                .param("key", scope.key());
    }

    private record StoredKey(String requestHash, String status, int responseStatus, String responseBody) {

        IdempotencyClaim claimFor(String hash) {
            if (!requestHash.equals(hash)) {
                return new IdempotencyClaim.Mismatch();
            }
            if (IN_PROGRESS.equals(status)) {
                return new IdempotencyClaim.InProgress();
            }
            return new IdempotencyClaim.Replay(responseStatus, responseBody);
        }
    }
}
