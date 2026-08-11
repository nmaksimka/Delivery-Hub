package com.deliveryhub.payment;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

abstract class PaymentApiSupport {

    private static final AtomicLong IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Autowired
    protected MockMvcTester mvc;
    @Autowired
    protected JdbcClient jdbcClient;

    protected static long nextId() {
        return IDS.incrementAndGet();
    }

    protected MvcTestResult topUp(long userId, String key, String amount) {
        return mvc.post().uri("/api/payments/wallet/top-up")
                .header("X-User-Id", String.valueOf(userId))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": " + amount + "}")
                .exchange();
    }

    protected void fund(long userId, String amount) {
        topUp(userId, UUID.randomUUID().toString(), amount).assertThat().hasStatusOk();
    }

    protected MvcTestResult wallet(long userId) {
        return mvc.get().uri("/api/payments/wallet").header("X-User-Id", String.valueOf(userId)).exchange();
    }

    protected BigDecimal available(long userId) {
        return amountAt(wallet(userId), "$.available");
    }

    protected BigDecimal held(long userId) {
        return amountAt(wallet(userId), "$.held");
    }

    protected long operations(long userId, String type) {
        return jdbcClient.sql("""
                        SELECT count(*) FROM wallet_operations o JOIN wallets w ON w.id = o.wallet_id
                        WHERE w.user_id = :userId AND o.type = :type
                        """)
                .param("userId", userId)
                .param("type", type)
                .query(Long.class)
                .single();
    }

    protected static String json(MvcTestResult result, String path) {
        return String.valueOf((Object) JsonPath.read(body(result), path));
    }

    protected static BigDecimal amountAt(MvcTestResult result, String path) {
        return new BigDecimal(json(result, path));
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
