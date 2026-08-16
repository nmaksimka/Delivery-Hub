package com.deliveryhub.order;

import com.deliveryhub.order.client.MenuQuote;
import com.deliveryhub.order.client.DeliveryQuotes;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.UserAddresses;
import com.deliveryhub.order.client.dto.DeliveryQuoteDto;
import com.deliveryhub.order.client.dto.UserAddressDto;
import com.deliveryhub.order.client.RestaurantServiceUnavailableException;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.idempotency.ExpiredIdempotencyKeysCleanup;
import com.deliveryhub.order.idempotency.IdempotencyClaim;
import com.deliveryhub.order.idempotency.IdempotencyFilter;
import com.deliveryhub.order.idempotency.IdempotencyKeys;
import com.deliveryhub.order.idempotency.IdempotencyScope;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, KafkaContainerConfig.class,
        RedisContainerConfig.class})
class IdempotencyIT {

    private static final long RESTAURANT_ID = 1L;
    private static final String ORDER_REQUEST = """
            {"restaurantId": 1, "orderItemsRequest": [{"menuItemId": 10, "quantity": 2}]}
            """;
    private static final String OTHER_ORDER_REQUEST = """
            {"restaurantId": 1, "orderItemsRequest": [{"menuItemId": 10, "quantity": 5}]}
            """;
    private static final int PARALLEL_REQUESTS = 8;

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private ExpiredIdempotencyKeysCleanup cleanup;
    @Autowired
    private IdempotencyKeys keys;
    @MockitoBean
    private RestaurantCatalog restaurantCatalog;
    @MockitoBean
    private UserAddresses userAddresses;
    @MockitoBean
    private DeliveryQuotes deliveryQuotes;

    @BeforeEach
    void stubRestaurantService() {
        when(userAddresses.deliverableAddress(any(), any())).thenReturn(new UserAddressDto(77L, "Томск", "Ленина",
                "12", null, null, null, null, new BigDecimal("56.47"), new BigDecimal("84.97"), true));
        when(deliveryQuotes.quote(any(), any(), any(UserAddressDto.class))).thenReturn(new DeliveryQuoteDto(1500, 20,
                new BigDecimal("0.00"), "OSRM", new DeliveryQuoteDto.Weather("CLEAR", 5.0, new BigDecimal("0.00"), 0)));
        when(restaurantCatalog.quote(eq(RESTAURANT_ID), any())).thenReturn(pizzeriaQuote());
    }

    @Test
    void retryWithSameKeyReturnsSameOrderWithoutCreatingAnother() {
        String key = UUID.randomUUID().toString();

        MvcTestResult first = createOrder(6001L, key, ORDER_REQUEST);
        MvcTestResult retry = createOrder(6001L, key, ORDER_REQUEST);

        assertThat(first).hasStatus(201);
        assertThat(retry).hasStatus(201).hasHeader(IdempotencyFilter.HEADER_REPLAYED, "true");
        assertThat(idOf(retry)).isEqualTo(idOf(first));
        assertThat(ordersOf(6001L)).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyIsRejected() {
        String key = UUID.randomUUID().toString();
        createOrder(6002L, key, ORDER_REQUEST);

        assertThat(createOrder(6002L, key, OTHER_ORDER_REQUEST)).hasStatus(422)
                .bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(ordersOf(6002L)).isEqualTo(1);
    }

    @Test
    void requestsWithoutKeyAreNotDeduplicated() {
        createOrder(6003L, null, ORDER_REQUEST);
        createOrder(6003L, null, ORDER_REQUEST);

        assertThat(ordersOf(6003L)).isEqualTo(2);
    }

    @Test
    void sameKeyOfDifferentUsersCreatesSeparateOrders() {
        String key = UUID.randomUUID().toString();

        MvcTestResult first = createOrder(6004L, key, ORDER_REQUEST);
        MvcTestResult second = createOrder(6005L, key, ORDER_REQUEST);

        assertThat(second).hasStatus(201).doesNotContainHeader(IdempotencyFilter.HEADER_REPLAYED);
        assertThat(idOf(second)).isNotEqualTo(idOf(first));
    }

    @Test
    void parallelRequestsWithSameKeyCreateExactlyOneOrder() throws Exception {
        when(restaurantCatalog.quote(eq(RESTAURANT_ID), any())).thenAnswer(invocation -> {
            Thread.sleep(300);
            return pizzeriaQuote();
        });
        String key = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> statuses;
        try (ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_REQUESTS)) {
            List<Future<Integer>> results = IntStream.range(0, PARALLEL_REQUESTS)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return createOrder(6006L, key, ORDER_REQUEST).getResponse().getStatus();
                    }))
                    .toList();
            start.countDown();
            statuses = results.stream().map(IdempotencyIT::await).toList();
        }

        assertThat(statuses).containsOnly(201, 409).contains(201);
        assertThat(ordersOf(6006L)).isEqualTo(1);
    }

    @Test
    void serverErrorReleasesKeySoRetrySucceeds() {
        String key = UUID.randomUUID().toString();
        when(restaurantCatalog.quote(eq(RESTAURANT_ID), any()))
                .thenThrow(new RestaurantServiceUnavailableException("down", new IllegalStateException()))
                .thenReturn(pizzeriaQuote());

        assertThat(createOrder(6007L, key, ORDER_REQUEST)).hasStatus(503);
        MvcTestResult retry = createOrder(6007L, key, ORDER_REQUEST);

        assertThat(retry).hasStatus(201).doesNotContainHeader(IdempotencyFilter.HEADER_REPLAYED);
        assertThat(ordersOf(6007L)).isEqualTo(1);
    }

    @Test
    void checkoutRetryReturnsCreatedOrderEvenThoughCartIsAlreadyEmpty() {
        String key = UUID.randomUUID().toString();
        addToCart(6008L);

        MvcTestResult first = checkout(6008L, key);
        MvcTestResult retry = checkout(6008L, key);

        assertThat(first).hasStatus(201);
        assertThat(retry).hasStatus(201).hasHeader(IdempotencyFilter.HEADER_REPLAYED, "true");
        assertThat(idOf(retry)).isEqualTo(idOf(first));
        assertThat(checkout(6008L, UUID.randomUUID().toString())).hasStatus(409)
                .bodyJson().extractingPath("$.code").isEqualTo("CART_EMPTY");
        assertThat(ordersOf(6008L)).isEqualTo(1);
    }

    @Test
    void clientErrorIsRememberedForTheSameKey() {
        String key = UUID.randomUUID().toString();
        assertThat(checkout(6009L, key)).hasStatus(409);
        addToCart(6009L);

        assertThat(checkout(6009L, key)).hasStatus(409).hasHeader(IdempotencyFilter.HEADER_REPLAYED, "true")
                .bodyJson().extractingPath("$.code").isEqualTo("CART_EMPTY");
        assertThat(ordersOf(6009L)).isZero();
    }

    @Test
    void rejectsMalformedKey() {
        assertThat(createOrder(6010L, "with space", ORDER_REQUEST)).hasStatus(400)
                .bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_INVALID");
        assertThat(ordersOf(6010L)).isZero();
    }

    @Test
    void expiredKeyIsRemovedByCleanupAndCanBeUsedAgain() {
        String key = UUID.randomUUID().toString();
        createOrder(6011L, key, ORDER_REQUEST);
        jdbcClient.sql("UPDATE idempotency_keys SET expires_at = now() - interval '1 minute' WHERE key = :key")
                .param("key", key)
                .update();

        cleanup.removeExpired();

        assertThat(keysNamed(key)).isZero();
        assertThat(createOrder(6011L, key, OTHER_ORDER_REQUEST)).hasStatus(201);
        assertThat(ordersOf(6011L)).isEqualTo(2);
    }

    @Test
    void expiredKeyNotYetCleanedUpIsTakenOverByNewRequest() {
        String key = UUID.randomUUID().toString();
        createOrder(6012L, key, ORDER_REQUEST);
        jdbcClient.sql("UPDATE idempotency_keys SET expires_at = now() - interval '1 minute' WHERE key = :key")
                .param("key", key)
                .update();

        assertThat(createOrder(6012L, key, OTHER_ORDER_REQUEST)).hasStatus(201)
                .doesNotContainHeader(IdempotencyFilter.HEADER_REPLAYED);
        assertThat(ordersOf(6012L)).isEqualTo(2);
    }

    @Test
    void claimHoldsKeyForShortLeaseAndCompletionKeepsItForFullTtl() {
        IdempotencyScope scope = new IdempotencyScope(6013L, "POST", "/api/orders", UUID.randomUUID().toString());

        assertThat(keys.claim(scope, "hash")).isInstanceOf(IdempotencyClaim.Acquired.class);
        Instant leasedUntil = expiresAt(scope.key());
        keys.complete(scope, 201, "{}");
        Instant keptUntil = expiresAt(scope.key());

        assertThat(leasedUntil).isBefore(Instant.now().plus(Duration.ofMinutes(2)));
        assertThat(keptUntil).isAfter(Instant.now().plus(Duration.ofHours(23)));
    }

    @Test
    void abandonedInProgressKeyIsTakenOverAfterLease() {
        String key = UUID.randomUUID().toString();
        jdbcClient.sql("""
                        INSERT INTO idempotency_keys (user_id, method, path, key, request_hash, status, created_at, expires_at)
                        VALUES (6014, 'POST', '/api/orders', :key, 'stale', 'IN_PROGRESS',
                                now() - interval '2 minutes', now() - interval '1 minute')
                        """)
                .param("key", key)
                .update();

        assertThat(createOrder(6014L, key, ORDER_REQUEST)).hasStatus(201)
                .doesNotContainHeader(IdempotencyFilter.HEADER_REPLAYED);
        assertThat(ordersOf(6014L)).isEqualTo(1);
    }

    @Test
    void tooLargeBodyIsRejectedBeforeKeyIsClaimed() {
        String key = UUID.randomUUID().toString();
        String huge = "{\"restaurantId\": 1, \"note\": \"" + "x".repeat(70 * 1024) + "\"}";

        assertThat(createOrder(6015L, key, huge)).hasStatus(413)
                .bodyJson().extractingPath("$.code").isEqualTo("REQUEST_TOO_LARGE");
        assertThat(keysNamed(key)).isZero();
    }

    private Instant expiresAt(String key) {
        return jdbcClient.sql("SELECT expires_at FROM idempotency_keys WHERE key = :key")
                .param("key", key)
                .query(Timestamp.class)
                .single()
                .toInstant();
    }

    private static MenuQuote pizzeriaQuote() {
        return new MenuQuote(new RestaurantDto(RESTAURANT_ID, "Pizzeria", true),
                Map.of(10L, new MenuItemDto(10L, "Pizza", new BigDecimal("12.50"), true)));
    }

    private MvcTestResult createOrder(long userId, String key, String body) {
        var request = mvc.post().uri("/api/orders")
                .header("X-User-Id", Long.toString(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (key != null) {
            request = request.header(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, key);
        }
        return request.exchange();
    }

    private MvcTestResult checkout(long userId, String key) {
        return mvc.post().uri("/api/cart/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\": 77}")
                .header("X-User-Id", Long.toString(userId))
                .header(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, key)
                .exchange();
    }

    private void addToCart(long userId) {
        assertThat(mvc.post().uri("/api/cart/items")
                .header("X-User-Id", Long.toString(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurantId\": 1, \"menuItemId\": 10, \"quantity\": 1}"))
                .hasStatusOk();
    }

    private long ordersOf(long userId) {
        return jdbcClient.sql("SELECT count(*) FROM orders WHERE user_id = :userId")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    private long keysNamed(String key) {
        return jdbcClient.sql("SELECT count(*) FROM idempotency_keys WHERE key = :key")
                .param("key", key)
                .query(Long.class)
                .single();
    }

    private static Integer idOf(MvcTestResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Integer await(Future<Integer> result) {
        try {
            return result.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
