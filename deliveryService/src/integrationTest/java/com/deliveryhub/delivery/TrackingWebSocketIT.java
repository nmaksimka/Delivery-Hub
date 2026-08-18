package com.deliveryhub.delivery;

import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.delivery.dto.UpdateDeliveryStatusRequest;
import com.deliveryhub.delivery.entity.Courier;
import com.deliveryhub.delivery.entity.Delivery;
import com.deliveryhub.delivery.entity.GeoPosition;
import com.deliveryhub.delivery.repository.CourierRepository;
import com.deliveryhub.delivery.repository.DeliveryRepository;
import com.deliveryhub.delivery.security.InternalTokenFilter;
import com.deliveryhub.delivery.service.DeliveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Type;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class TrackingWebSocketIT {

    private static final AtomicLong IDS = new AtomicLong(770_000);
    private static final GeoPosition RESTAURANT = new GeoPosition(56.4884, 84.9480);
    private static final GeoPosition CUSTOMER = new GeoPosition(56.4697, 84.9483);

    @Value("${local.server.port}")
    private int port;

    @Value("${app.internal-token}")
    private String internalToken;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private DeliveryService deliveryService;

    private final List<StompSession> sessions = new ArrayList<>();

    @DynamicPropertySource
    static void osrmIsDown(DynamicPropertyRegistry registry) {
        registry.add("app.routing.base-url", () -> "http://127.0.0.1:9");
        registry.add("app.routing.connect-timeout", () -> "200ms");
    }

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    @Test
    void ownerReceivesCourierPositionWithEtaAndStatusChanges() {
        Courier courier = courierWithAccount();
        Delivery delivery = deliveryOnTheWay(42L, courier.getId());
        BlockingQueue<JsonNode> updates = new LinkedBlockingQueue<>();
        subscribe(connect(42L, "USER"), delivery.getOrderId(), updates);

        JsonNode position = awaitUpdate(updates, () -> reportLocation(courier.getUserId(), 56.4800, 84.9481),
                "POSITION");

        assertThat(position.get("orderId").asLong()).isEqualTo(delivery.getOrderId());
        assertThat(position.get("deliveryId").asLong()).isEqualTo(delivery.getId());
        assertThat(position.get("status").asString()).isEqualTo("PICKED_UP");
        assertThat(position.get("courier").get("latitude").asDouble()).isEqualTo(56.48);
        assertThat(position.get("courier").get("longitude").asDouble()).isEqualTo(84.9481);
        assertThat(position.get("courier").get("heading").asDouble()).isBetween(0.0, 360.0);
        assertThat(position.get("courier").hasNonNull("ts")).isTrue();
        assertThat(position.get("remainingMeters").asLong()).isBetween(1_000L, 1_300L);
        assertThat(position.get("etaSeconds").asLong()).isPositive();
        assertThat(Instant.parse(position.get("estimatedArrival").asString()))
                .isAfter(Instant.parse(position.get("at").asString()));

        deliveryService.updateDeliveryStatus(delivery.getId(),
                new UpdateDeliveryStatusRequest(DeliveryStatus.IN_TRANSIT, null));
        JsonNode status = awaitUpdate(updates, () -> { }, "STATUS");

        assertThat(status.get("status").asString()).isEqualTo("IN_TRANSIT");
        assertThat(status.path("courier").isNull() || status.path("courier").isMissingNode()).isTrue();
    }

    @Test
    void foreignUserSubscriptionIsRejectedWithErrorFrame() {
        Delivery delivery = deliveryOnTheWay(42L, courierWithAccount().getId());
        CompletableFuture<StompHeaders> error = new CompletableFuture<>();

        StompSession session = connect(43L, "USER", error);
        session.subscribe("/topic/orders/" + delivery.getOrderId() + "/tracking", ignoring());

        assertThat(error).succeedsWithin(Duration.ofSeconds(10))
                .satisfies(headers -> assertThat(headers.getFirst("message")).contains("not available"));
    }

    @Test
    void onlyAssignedCourierAndAdminMayWatchOrder() {
        Courier assigned = courierWithAccount();
        Courier stranger = courierWithAccount();
        Delivery delivery = deliveryOnTheWay(42L, assigned.getId());

        BlockingQueue<JsonNode> courierUpdates = new LinkedBlockingQueue<>();
        BlockingQueue<JsonNode> adminUpdates = new LinkedBlockingQueue<>();
        subscribe(connect(assigned.getUserId(), "COURIER"), delivery.getOrderId(), courierUpdates);
        subscribe(connect(1L, "ADMIN"), delivery.getOrderId(), adminUpdates);
        CompletableFuture<StompHeaders> strangerError = new CompletableFuture<>();
        connect(stranger.getUserId(), "COURIER", strangerError)
                .subscribe("/topic/orders/" + delivery.getOrderId() + "/tracking", ignoring());

        awaitUpdate(courierUpdates, () -> reportLocation(assigned.getUserId(), 56.4850, 84.9480), "POSITION");
        awaitUpdate(adminUpdates, () -> reportLocation(assigned.getUserId(), 56.4840, 84.9480), "POSITION");
        assertThat(strangerError).succeedsWithin(Duration.ofSeconds(10));
    }

    @Test
    void subscriptionToOtherDestinationsIsRejected() {
        CompletableFuture<StompHeaders> error = new CompletableFuture<>();

        connect(1L, "ADMIN", error).subscribe("/topic/couriers", ignoring());

        assertThat(error).succeedsWithin(Duration.ofSeconds(10))
                .satisfies(headers -> assertThat(headers.getFirst("message")).contains("/tracking"));
    }

    @Test
    void handshakeWithoutGatewayTokenIsRefused() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("X-User-Id", "42");
        headers.add("X-User-Role", "ADMIN");

        assertThatThrownBy(() -> stompClient()
                .connectAsync(URI.create(url()), headers, new StompHeaders(), new StompSessionHandlerAdapter() { })
                .get(10, TimeUnit.SECONDS))
                .hasStackTraceContaining("401");
    }

    private Courier courierWithAccount() {
        long id = IDS.incrementAndGet();
        return courierRepository.saveAndFlush(Courier.register(id, "Курьер " + id, "+7999" + id, Instant.now()));
    }

    private Delivery deliveryOnTheWay(long userId, long courierId) {
        Delivery delivery = Delivery.forOrder(IDS.incrementAndGet(), userId, courierId, null, Instant.now());
        delivery.routeBetween(RESTAURANT, CUSTOMER);
        delivery.changeStatus(DeliveryStatus.PICKED_UP, Instant.now());
        return deliveryRepository.saveAndFlush(delivery);
    }

    private void reportLocation(long userId, double latitude, double longitude) {
        RestClient.create().put()
                .uri("http://localhost:" + port + "/api/couriers/me/location")
                .header(InternalTokenFilter.HEADER_INTERNAL_TOKEN, internalToken)
                .header("X-User-Id", Long.toString(userId))
                .header("X-User-Role", "COURIER")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"latitude\": " + latitude + ", \"longitude\": " + longitude + "}")
                .retrieve()
                .toBodilessEntity();
    }

    private static JsonNode awaitUpdate(BlockingQueue<JsonNode> updates, Runnable trigger, String type) {
        List<JsonNode> found = new ArrayList<>();
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(300)).until(() -> {
            trigger.run();
            JsonNode update = updates.poll(200, TimeUnit.MILLISECONDS);
            while (update != null) {
                if (type.equals(update.get("type").asString())) {
                    found.add(update);
                    return true;
                }
                update = updates.poll();
            }
            return false;
        });
        return found.getFirst();
    }

    private void subscribe(StompSession session, long orderId, BlockingQueue<JsonNode> updates) {
        session.subscribe("/topic/orders/" + orderId + "/tracking", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                updates.add((JsonNode) payload);
            }
        });
    }

    private StompSession connect(long userId, String role) {
        return connect(userId, role, new CompletableFuture<>());
    }

    private StompSession connect(long userId, String role, CompletableFuture<StompHeaders> errors) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(InternalTokenFilter.HEADER_INTERNAL_TOKEN, internalToken);
        headers.add("X-User-Id", Long.toString(userId));
        headers.add("X-User-Role", role);
        try {
            StompSession session = stompClient()
                    .connectAsync(URI.create(url()), headers, new StompHeaders(), new StompSessionHandlerAdapter() {
                        @Override
                        public void handleFrame(StompHeaders frameHeaders, Object payload) {
                            errors.complete(frameHeaders);
                        }
                    })
                    .get(10, TimeUnit.SECONDS);
            sessions.add(session);
            return session;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        return client;
    }

    private String url() {
        return "ws://localhost:" + port + "/ws/tracking";
    }

    private static StompFrameHandler ignoring() {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
            }
        };
    }
}
