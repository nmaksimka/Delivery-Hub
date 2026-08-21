package com.deliveryhub.delivery;

import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.repository.CourierRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class, KafkaContainerConfig.class})
class DeliveryApiIT {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private CourierRepository courierRepository;

    @Test
    void createsDeliveryAndMovesItThroughStatuses() {
        long courierId = availableCourier("+79990008001");
        MvcTestResult created = postJson("/api/deliveries", """
                {"orderId": 8001, "courierId": %d}
                """.formatted(courierId));
        assertThat(created).hasStatus(201).bodyJson().extractingPath("$.status").isEqualTo("ASSIGNED");
        Integer deliveryId = JsonPath.read(bodyOf(created), "$.id");

        assertThat(patchStatus(deliveryId, "DELIVERED")).hasStatus(409);
        assertThat(patchStatus(deliveryId, "PICKED_UP")).hasStatusOk();
        assertThat(patchStatus(deliveryId, "IN_TRANSIT")).hasStatusOk();
        assertThat(patchStatus(deliveryId, "DELIVERED")).hasStatusOk();
        assertThat(patchStatus(deliveryId, "IN_TRANSIT")).hasStatus(409);
        assertThat(mvc.get().uri("/api/deliveries/order/8001").header("X-User-Id", "3").header("X-User-Role", "COURIER"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("DELIVERED");
    }

    @Test
    void customerSeesOnlyOwnDelivery() {
        MvcTestResult created = postJson("/api/deliveries", "{\"orderId\": 8010, \"userId\": 77}");
        Integer deliveryId = JsonPath.read(bodyOf(created), "$.id");

        assertThat(mvc.get().uri("/api/deliveries/" + deliveryId).header("X-User-Id", "77")).hasStatusOk();
        assertThat(mvc.get().uri("/api/deliveries/order/8010").header("X-User-Id", "77")).hasStatusOk();
        assertThat(mvc.get().uri("/api/deliveries/" + deliveryId).header("X-User-Id", "78")).hasStatus(403);
        assertThat(mvc.get().uri("/api/deliveries/order/8010").header("X-User-Id", "78")).hasStatus(403);
        assertThat(mvc.get().uri("/api/deliveries/" + deliveryId).header("X-User-Id", "78").header("X-User-Role", "ADMIN"))
                .hasStatusOk();
    }

    @Test
    void listIsClosedForCustomers() {
        assertThat(mvc.get().uri("/api/deliveries").header("X-User-Id", "77")).hasStatus(403);
        assertThat(mvc.get().uri("/api/deliveries").header("X-User-Id", "5").header("X-User-Role", "COURIER"))
                .hasStatusOk();
        assertThat(mvc.get().uri("/api/deliveries")).hasStatus(400);
    }

    @Test
    void refusesSecondDeliveryForSameOrder() {
        String body = """
                {"orderId": 8002}
                """;
        postJson("/api/deliveries", body);

        assertThat(postJson("/api/deliveries", body)).hasStatus(409);
    }

    @Test
    void refusesDeliveryForOrderCancelledEarlier() {
        jdbcClient.sql("INSERT INTO cancelled_orders (order_id, cancelled_at) VALUES (8099, now()) ON CONFLICT DO NOTHING")
                .update();

        assertThat(postJson("/api/deliveries", "{\"orderId\": 8099}")).hasStatus(409);
        assertThat(mvc.get().uri("/api/deliveries/order/8099").header("X-User-Id", "1").header("X-User-Role", "ADMIN"))
                .hasStatus(404);
    }

    @Test
    void concurrentCreationForSameOrderGivesOneConflict() throws Exception {
        int attempts = 6;
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                statuses.add(executor.submit(() -> {
                    start.await();
                    return postJson("/api/deliveries", "{\"orderId\": 8020}").getResponse().getStatus();
                }));
            }
            start.countDown();

            List<Integer> results = new ArrayList<>();
            for (Future<Integer> status : statuses) {
                results.add(status.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).containsOnly(201, 409);
            assertThat(results).filteredOn(status -> status == 201).hasSize(1);
        }
    }

    @Test
    void requiresOrderId() {
        assertThat(postJson("/api/deliveries", "{}")).hasStatus(400)
                .bodyJson().extractingPath("$.errors.orderId").isEqualTo("Order ID is required");
    }

    @Test
    void assignsCourierWithoutStatusAndRejectsEmptyPatch() {
        MvcTestResult created = postJson("/api/deliveries", "{\"orderId\": 8003}");
        Integer deliveryId = JsonPath.read(bodyOf(created), "$.id");
        String uri = "/api/deliveries/" + deliveryId + "/status";
        long courierId = availableCourier("+79990008003");

        assertThat(asAdmin(mvc.patch().uri(uri)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"courierId\": " + courierId + "}"))
                .hasStatusOk().bodyJson().extractingPath("$.courierId").isEqualTo((int) courierId);
        assertThat(asAdmin(mvc.patch().uri(uri)).contentType(MediaType.APPLICATION_JSON).content("{}")).hasStatus(400);
    }

    @Test
    void onlyAdminCreatesDeliveryAndOnlyAssignedCourierChangesStatus() {
        assertThat(mvc.post().uri("/api/deliveries").header("X-User-Id", "77")
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\": 8004}")).hasStatus(403);
        Integer deliveryId = JsonPath.read(bodyOf(postJson("/api/deliveries", "{\"orderId\": 8005}")), "$.id");

        assertThat(mvc.patch().uri("/api/deliveries/" + deliveryId + "/status")
                .header("X-User-Id", "5").header("X-User-Role", "COURIER")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"PICKED_UP\"}")).hasStatus(403);
    }

    private long availableCourier(String phone) {
        Courier courier = Courier.register(null, "Курьер", phone, Instant.now());
        courier.goOnline(Instant.now());
        return courierRepository.saveAndFlush(courier).getId();
    }

    private static MockMvcTester.MockMvcRequestBuilder asAdmin(MockMvcTester.MockMvcRequestBuilder request) {
        return request.header("X-User-Id", "1").header("X-User-Role", "ADMIN");
    }

    private MvcTestResult patchStatus(Integer deliveryId, String status) {
        return asAdmin(mvc.patch().uri("/api/deliveries/" + deliveryId + "/status"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + status + "\"}")
                .exchange();
    }

    private MvcTestResult postJson(String uri, String json) {
        return asAdmin(mvc.post().uri(uri)).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
