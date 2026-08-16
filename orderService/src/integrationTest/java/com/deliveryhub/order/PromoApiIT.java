package com.deliveryhub.order;

import com.deliveryhub.order.client.MenuQuote;
import com.deliveryhub.order.client.DeliveryQuotes;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.UserAddresses;
import com.deliveryhub.order.client.dto.DeliveryQuoteDto;
import com.deliveryhub.order.client.dto.UserAddressDto;
import com.deliveryhub.order.client.dto.MenuItemDto;
import com.deliveryhub.order.client.dto.RestaurantDto;
import com.deliveryhub.order.idempotency.IdempotencyFilter;
import com.deliveryhub.order.promo.PromoCode;
import com.deliveryhub.order.promo.PromoCodeRepository;
import com.deliveryhub.order.promo.PromoRedemptionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, KafkaContainerConfig.class,
        RedisContainerConfig.class})
class PromoApiIT {

    private static final AtomicLong USERS = new AtomicLong(5000);

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private PromoCodeRepository promoCodes;
    @Autowired
    private PromoRedemptionRepository redemptions;
    @MockitoBean
    private RestaurantCatalog restaurantCatalog;
    @MockitoBean
    private UserAddresses userAddresses;
    @MockitoBean
    private DeliveryQuotes deliveryQuotes;

    @BeforeEach
    void stubRestaurants() {
        when(userAddresses.deliverableAddress(any(), any())).thenReturn(new UserAddressDto(77L, "Томск", "Ленина",
                "12", null, null, null, null, new BigDecimal("56.47"), new BigDecimal("84.97"), true));
        when(deliveryQuotes.quote(any(), any(), any(UserAddressDto.class))).thenReturn(new DeliveryQuoteDto(1500, 20,
                new BigDecimal("0.00"), "OSRM", new DeliveryQuoteDto.Weather("CLEAR", 5.0, new BigDecimal("0.00"), 0)));
        when(restaurantCatalog.quote(eq(1L), any())).thenReturn(new MenuQuote(new RestaurantDto(1L, "Pizzeria", true),
                Map.of(10L, new MenuItemDto(10L, "Pizza", new BigDecimal("12.50"), true))));
        when(restaurantCatalog.quote(eq(2L), any())).thenReturn(new MenuQuote(new RestaurantDto(2L, "Sushi", true),
                Map.of(20L, new MenuItemDto(20L, "Roll", new BigDecimal("8.00"), true))));
    }

    @Test
    void onlyAdminManagesPromoCodes() {
        assertThat(createPromo("USER-ATTEMPT", "PERCENT", "10", "{}", "USER")).hasStatus(403);
        assertThat(mvc.get().uri("/api/admin/promo-codes").exchange()).hasStatus(403);

        MvcTestResult created = createPromo("admin-made", "PERCENT", "10", "{}", "ADMIN");
        assertThat(created).hasStatus(201).bodyJson().extractingPath("$.code").isEqualTo("ADMIN-MADE");
        Integer id = JsonPath.read(bodyOf(created), "$.id");

        assertThat(mvc.get().uri("/api/admin/promo-codes").header("X-User-Role", "ADMIN").exchange())
                .hasStatusOk().bodyJson().extractingPath("$.content").asArray().isNotEmpty();
        assertThat(mvc.post().uri("/api/admin/promo-codes/" + id + "/deactivate").header("X-User-Role", "USER")
                .exchange()).hasStatus(403);
        assertThat(mvc.post().uri("/api/admin/promo-codes/" + id + "/deactivate").header("X-User-Role", "ADMIN")
                .exchange()).hasStatusOk().bodyJson().extractingPath("$.active").isEqualTo(false);
    }

    @Test
    void duplicateCodeIgnoringCaseAndInvalidDataAreRejected() {
        createPromo("DUP-CODE", "FIXED", "5", "{}", "ADMIN");

        assertThat(createPromo("dup-code", "FIXED", "5", "{}", "ADMIN")).hasStatus(409)
                .bodyJson().extractingPath("$.code").isEqualTo("PROMO_CODE_EXISTS");
        assertThat(createPromo("BAD-PERCENT", "PERCENT", "150", "{}", "ADMIN")).hasStatus(400)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
        assertThat(createPromo("X", "FIXED", "5", "{}", "ADMIN")).hasStatus(400);
    }

    @Test
    void checkoutWithPromoFixesDiscountInOrderAndRecordsRedemption() {
        createPromo("SAVE10", "PERCENT", "10", "{}", "ADMIN");
        long user = nextUser();
        add(user, 1, 10, 2);

        MvcTestResult applied = apply(user, "save10");
        assertThat(applied).hasStatusOk();
        assertThat(applied).bodyJson().extractingPath("$.subtotal").isEqualTo(25.0);
        assertThat(applied).bodyJson().extractingPath("$.discount").isEqualTo(2.5);
        assertThat(applied).bodyJson().extractingPath("$.total").isEqualTo(22.5);
        assertThat(applied).bodyJson().extractingPath("$.promoCode").isEqualTo("SAVE10");
        assertThat(cart(user)).bodyJson().extractingPath("$.promoCode").isEqualTo("SAVE10");

        MvcTestResult order = checkout(user);

        assertThat(order).hasStatus(201).bodyJson().extractingPath("$.totalAmount").isEqualTo(22.5);
        assertThat(order).bodyJson().extractingPath("$.discount").isEqualTo(2.5);
        assertThat(order).bodyJson().extractingPath("$.promoCode").isEqualTo("SAVE10");
        Integer orderId = JsonPath.read(bodyOf(order), "$.id");
        assertThat(redemptions.findByOrderId(orderId.longValue())).isPresent();
        assertThat(promoCodes.findByCode("SAVE10")).get().extracting(PromoCode::getUsedCount).isEqualTo(1);
        assertThat(cart(user)).bodyJson().extractingPath("$.items").asArray().isEmpty();
    }

    @Test
    void removingPromoDropsDiscountFromCart() {
        createPromo("SAVE5", "FIXED", "5", "{}", "ADMIN");
        long user = nextUser();
        add(user, 1, 10, 2);
        apply(user, "SAVE5");

        MvcTestResult removed = mvc.delete().uri("/api/cart/promo").header("X-User-Id", Long.toString(user)).exchange();

        assertThat(removed).hasStatusOk().bodyJson().extractingPath("$.promoCode").isNull();
        assertThat(removed).bodyJson().extractingPath("$.total").isEqualTo(25.0);
        assertThat(checkout(user)).hasStatus(201).bodyJson().extractingPath("$.discount").isEqualTo(0);
    }

    @Test
    void cancellingOrderReleasesRedemptionSoPerUserLimitIsFreedAgain() {
        createPromo("ONCE", "FIXED", "5", "{\"maxPerUser\": 1}", "ADMIN");
        long user = nextUser();
        add(user, 1, 10, 2);
        apply(user, "ONCE");
        Integer orderId = JsonPath.read(bodyOf(checkout(user)), "$.id");

        add(user, 1, 10, 2);
        assertThat(apply(user, "ONCE")).hasStatus(409).bodyJson().extractingPath("$.code")
                .isEqualTo("PROMO_USER_LIMIT_REACHED");

        assertThat(mvc.post().uri("/api/orders/" + orderId + "/cancel").header("X-User-Id", Long.toString(user))
                .exchange()).hasStatusOk();

        assertThat(redemptions.findByOrderId(orderId.longValue())).isEmpty();
        assertThat(promoCodes.findByCode("ONCE")).get().extracting(PromoCode::getUsedCount).isEqualTo(0);
        assertThat(apply(user, "ONCE")).hasStatusOk();
    }

    @Test
    void applyingPromoReportsReasonCodes() {
        createPromo("MIN30", "FIXED", "5", "{\"minOrderAmount\": 30}", "ADMIN");
        createPromo("SUSHI-ONLY", "FIXED", "1", "{\"restaurantId\": 2}", "ADMIN");
        createPromo("OLD-ONE", "FIXED", "1",
                "{\"validFrom\": \"2020-01-01T00:00:00Z\", \"validTo\": \"2021-01-01T00:00:00Z\"}", "ADMIN");
        long user = nextUser();

        assertThat(apply(user, "MIN30")).hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("CART_EMPTY");
        add(user, 1, 10, 2);

        assertThat(apply(user, "NO-SUCH")).hasStatus(404).bodyJson().extractingPath("$.code")
                .isEqualTo("PROMO_NOT_FOUND");
        assertThat(apply(user, "MIN30")).hasStatus(409).bodyJson().extractingPath("$.code")
                .isEqualTo("PROMO_MIN_AMOUNT");
        assertThat(apply(user, "SUSHI-ONLY")).hasStatus(409).bodyJson().extractingPath("$.code")
                .isEqualTo("PROMO_OTHER_RESTAURANT");
        assertThat(apply(user, "OLD-ONE")).hasStatus(409).bodyJson().extractingPath("$.code")
                .isEqualTo("PROMO_EXPIRED");
        assertThat(cart(user)).bodyJson().extractingPath("$.promoCode").isNull();
    }

    @Test
    void totalLimitIsNotExceededUnderConcurrentCheckouts() throws Exception {
        int users = 12;
        int limit = 3;
        createPromo("RACE", "FIXED", "2", "{\"maxRedemptions\": " + limit + "}", "ADMIN");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            long user = nextUser();
            ids.add(user);
            add(user, 1, 10, 1);
            assertThat(apply(user, "RACE")).hasStatusOk();
        }

        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcTestResult>> results = new ArrayList<>();
        for (long user : ids) {
            results.add(pool.submit(() -> {
                start.await();
                return checkout(user);
            }));
        }
        start.countDown();
        int created = 0;
        int rejected = 0;
        for (Future<MvcTestResult> future : results) {
            MvcTestResult result = future.get();
            if (result.getResponse().getStatus() == 201) {
                created++;
            } else {
                assertThat(result).hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("PROMO_LIMIT_REACHED");
                rejected++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(limit);
        assertThat(rejected).isEqualTo(users - limit);
        assertThat(promoCodes.findByCode("RACE")).get().extracting(PromoCode::getUsedCount).isEqualTo(limit);
        long recorded = ids.stream().mapToLong(user -> redemptions.countByPromoIdAndUserId(
                promoCodes.findByCode("RACE").orElseThrow().getId(), user)).sum();
        assertThat(recorded).isEqualTo(limit);
    }

    @Test
    void concurrentCreationOfSameCodeGivesOneCreatedAndConflictsWithCode() throws Exception {
        int admins = 6;
        ExecutorService pool = Executors.newFixedThreadPool(admins);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcTestResult>> results = new ArrayList<>();
        for (int i = 0; i < admins; i++) {
            String spelling = i % 2 == 0 ? "twins" : "TWINS";
            results.add(pool.submit(() -> {
                start.await();
                return createPromo(spelling, "FIXED", "1", "{}", "ADMIN");
            }));
        }
        start.countDown();
        int created = 0;
        for (Future<MvcTestResult> future : results) {
            MvcTestResult result = future.get();
            if (result.getResponse().getStatus() == 201) {
                created++;
            } else {
                assertThat(result).hasStatus(409).bodyJson().extractingPath("$.code").isEqualTo("PROMO_CODE_EXISTS");
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
    }

    private static long nextUser() {
        return USERS.incrementAndGet();
    }

    private MvcTestResult createPromo(String code, String type, String value, String extra, String role) {
        String extraFields = extra.length() > 2 ? ", " + extra.substring(1, extra.length() - 1) : "";
        return mvc.post().uri("/api/admin/promo-codes")
                .header("X-User-Role", role)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s\", \"type\": \"%s\", \"value\": %s%s}".formatted(code, type, value, extraFields))
                .exchange();
    }

    private MvcTestResult cart(long user) {
        return mvc.get().uri("/api/cart").header("X-User-Id", Long.toString(user)).exchange();
    }

    private MvcTestResult add(long user, int restaurantId, int menuItemId, int quantity) {
        return mvc.post().uri("/api/cart/items?replace=true")
                .header("X-User-Id", Long.toString(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurantId\": %d, \"menuItemId\": %d, \"quantity\": %d}"
                        .formatted(restaurantId, menuItemId, quantity))
                .exchange();
    }

    private MvcTestResult apply(long user, String code) {
        return mvc.post().uri("/api/cart/promo")
                .header("X-User-Id", Long.toString(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"" + code + "\"}")
                .exchange();
    }

    private MvcTestResult checkout(long user) {
        return mvc.post().uri("/api/cart/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\": 77}")
                .header("X-User-Id", Long.toString(user))
                .header(IdempotencyFilter.HEADER_IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                .exchange();
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
