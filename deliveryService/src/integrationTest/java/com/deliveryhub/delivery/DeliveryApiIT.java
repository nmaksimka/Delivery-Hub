package com.deliveryhub.delivery;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
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

    @Test
    void createsDeliveryAndMovesItThroughStatuses() {
        MvcTestResult created = postJson("/api/deliveries", """
                {"orderId": 8001, "courierId": 5}
                """);
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

        assertThat(mvc.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).content("{\"courierId\": 9}"))
                .hasStatusOk().bodyJson().extractingPath("$.courierId").isEqualTo(9);
        assertThat(mvc.patch().uri(uri).contentType(MediaType.APPLICATION_JSON).content("{}")).hasStatus(400);
    }

    private MvcTestResult patchStatus(Integer deliveryId, String status) {
        return mvc.patch().uri("/api/deliveries/" + deliveryId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + status + "\"}")
                .exchange();
    }

    private MvcTestResult postJson(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String bodyOf(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
