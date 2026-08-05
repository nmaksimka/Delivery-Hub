package com.deliveryhub.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class})
class WalletApiIT extends PaymentApiSupport {

    @Test
    void newUserSeesEmptyWalletWithoutCreatingIt() {
        long userId = nextId();

        MvcTestResult result = wallet(userId);

        assertThat(result).hasStatusOk();
        assertThat(amountAt(result, "$.available")).isEqualByComparingTo("0");
        assertThat(amountAt(result, "$.held")).isEqualByComparingTo("0");
        assertThat(jdbcClient.sql("SELECT count(*) FROM wallets WHERE user_id = :id").param("id", userId)
                .query(Long.class).single()).isZero();
    }

    @Test
    void topUpAddsMoneyAndRecordsOperation() {
        long userId = nextId();

        MvcTestResult result = topUp(userId, UUID.randomUUID().toString(), "250.50");

        assertThat(result).hasStatusOk().doesNotContainHeader("Idempotent-Replayed");
        assertThat(amountAt(result, "$.available")).isEqualByComparingTo("250.50");
        assertThat(operations(userId, "TOP_UP")).isEqualTo(1);
    }

    @Test
    void retryWithSameKeyDoesNotAddMoneyTwice() {
        long userId = nextId();
        String key = UUID.randomUUID().toString();

        topUp(userId, key, "100").assertThat().hasStatusOk();
        MvcTestResult retry = topUp(userId, key, "100.00");

        assertThat(retry).hasStatusOk().hasHeader("Idempotent-Replayed", "true");
        assertThat(available(userId)).isEqualByComparingTo("100.00");
        assertThat(operations(userId, "TOP_UP")).isEqualTo(1);
    }

    @Test
    void sameKeyWithAnotherAmountIsRejected() {
        long userId = nextId();
        String key = UUID.randomUUID().toString();
        topUp(userId, key, "100").assertThat().hasStatusOk();

        MvcTestResult reused = topUp(userId, key, "200");

        assertThat(reused).hasStatus(422);
        assertThat(json(reused, "$.code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(available(userId)).isEqualByComparingTo("100.00");
    }

    @Test
    void sameKeyOfAnotherUserIsIndependent() {
        long first = nextId();
        long second = nextId();
        String key = UUID.randomUUID().toString();

        topUp(first, key, "10").assertThat().hasStatusOk();
        topUp(second, key, "20").assertThat().hasStatusOk().doesNotContainHeader("Idempotent-Replayed");

        assertThat(available(second)).isEqualByComparingTo("20.00");
    }

    @Test
    void topUpWithoutIdempotencyKeyIsBadRequest() {
        MvcTestResult result = mvc.post().uri("/api/payments/wallet/top-up")
                .header("X-User-Id", String.valueOf(nextId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 10}")
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(json(result, "$.code")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void topUpWithMalformedKeyIsBadRequest() {
        MvcTestResult result = topUp(nextId(), "key with spaces", "10");

        assertThat(result).hasStatus(400);
        assertThat(json(result, "$.code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void topUpAmountIsValidated() {
        long userId = nextId();

        assertThat(topUp(userId, UUID.randomUUID().toString(), "0")).hasStatus(400);
        assertThat(topUp(userId, UUID.randomUUID().toString(), "50000.01")).hasStatus(400);
        MvcTestResult fractional = topUp(userId, UUID.randomUUID().toString(), "10.001");
        assertThat(fractional).hasStatus(400);
        assertThat(json(fractional, "$.code")).isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void walletCannotGrowAboveDemoLimit() {
        long userId = nextId();
        fund(userId, "50000");
        fund(userId, "50000");

        MvcTestResult over = topUp(userId, UUID.randomUUID().toString(), "1");

        assertThat(over).hasStatus(422);
        assertThat(json(over, "$.code")).isEqualTo("WALLET_LIMIT_EXCEEDED");
        assertThat(available(userId)).isEqualByComparingTo("100000.00");
    }
}
