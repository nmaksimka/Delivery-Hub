package com.deliveryhub.notification;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.enums.DeliveryStatus;
import com.deliveryhub.contracts.events.CourierAssignedEvent;
import com.deliveryhub.contracts.events.DeliveryStatusUpdatedEvent;
import com.deliveryhub.contracts.events.OrderCreatedEvent;
import com.deliveryhub.contracts.events.PaymentRejectedEvent;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"app.mail.poll-interval=200ms", "app.mail.retry-backoff=100ms"})
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class, MailpitContainerConfig.class,
        GatewayTokenConfig.class})
class NotificationFlowIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final AtomicLong IDS = new AtomicLong(System.nanoTime() % 1_000_000_000L);
    private static final WireMockServer USERS = new WireMockServer(options().dynamicPort());

    static {
        USERS.start();
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private GenericContainer<?> mailpit;

    @DynamicPropertySource
    static void usersAreWireMock(DynamicPropertyRegistry registry) {
        registry.add("app.users.base-url", USERS::baseUrl);
    }

    @AfterAll
    static void stopUsers() {
        USERS.stop();
    }

    @Test
    void createdOrderSendsMailAndAppearsInHistory() {
        long userId = IDS.incrementAndGet();
        long orderId = IDS.incrementAndGet();
        String email = "user" + userId + "@example.com";
        userExists(userId, email, "Анна");

        send(Topics.ORDER_CREATED, orderId, orderCreated(UUID.randomUUID(), orderId, userId));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(mailsTo(email)).isEqualTo(1));
        assertThat(mailSubjectsTo(email)).containsExactly("Заказ №" + orderId + " принят");

        MvcTestResult history = mvc.get().uri("/api/notifications").header("X-User-Id", userId).exchange();
        assertThat(history).hasStatusOk().bodyJson().extractingPath("$.items[0].type").isEqualTo("ORDER_CREATED");
        assertThat(history).bodyJson().extractingPath("$.unread").isEqualTo(1);
    }

    @Test
    void duplicateEventGivesOneNotification() {
        long userId = IDS.incrementAndGet();
        long orderId = IDS.incrementAndGet();
        userExists(userId, "dup" + userId + "@example.com", "Иван");
        UUID eventId = UUID.randomUUID();
        PaymentRejectedEvent rejected = new PaymentRejectedEvent(eventId, orderId, userId, new BigDecimal("80.00"),
                "PAYMENT_INSUFFICIENT_FUNDS", Instant.now());

        send(Topics.PAYMENT_REJECTED, orderId, rejected);
        send(Topics.PAYMENT_REJECTED, orderId, rejected);
        long markerOrderId = IDS.incrementAndGet();
        send(Topics.PAYMENT_REJECTED, markerOrderId, new PaymentRejectedEvent(UUID.randomUUID(), markerOrderId,
                userId, BigDecimal.ONE, "PAYMENT_INSUFFICIENT_FUNDS", Instant.now()));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(notificationsOf(userId)).isEqualTo(2));
        assertThat(notificationsOfOrder(orderId)).isEqualTo(1);
    }

    @Test
    void courierAndDeliveryReachOrderOwner() {
        long userId = IDS.incrementAndGet();
        long orderId = IDS.incrementAndGet();
        userExists(userId, "owner" + userId + "@example.com", "Олег");

        send(Topics.ORDER_CREATED, orderId, orderCreated(UUID.randomUUID(), orderId, userId));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(notificationsOf(userId)).isEqualTo(1));
        send(Topics.COURIER_ASSIGNED, orderId, new CourierAssignedEvent(UUID.randomUUID(), 1L, orderId, 5L,
                Instant.now(), 2L));
        send(Topics.DELIVERY_STATUS_UPDATED, orderId, new DeliveryStatusUpdatedEvent(UUID.randomUUID(), 1L, orderId,
                DeliveryStatus.IN_TRANSIT, Instant.now(), 3L));
        send(Topics.DELIVERY_STATUS_UPDATED, orderId, new DeliveryStatusUpdatedEvent(UUID.randomUUID(), 1L, orderId,
                DeliveryStatus.DELIVERED, Instant.now(), 4L));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(typesOf(userId))
                .containsExactlyInAnyOrder("ORDER_CREATED", "COURIER_ASSIGNED", "ORDER_DELIVERED"));
    }

    @Test
    void unknownRecipientMarksMailFailedButKeepsHistory() {
        long userId = IDS.incrementAndGet();
        long orderId = IDS.incrementAndGet();
        USERS.stubFor(get(urlEqualTo("/internal/users/" + userId + "/contact")).willReturn(aResponse().withStatus(404)));

        send(Topics.ORDER_CREATED, orderId, orderCreated(UUID.randomUUID(), orderId, userId));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(mailStatusOf(orderId)).isEqualTo("FAILED"));
        assertThat(notificationsOf(userId)).isEqualTo(1);
    }

    @Test
    void userReadsOnlyOwnNotifications() {
        long userId = IDS.incrementAndGet();
        long orderId = IDS.incrementAndGet();
        userExists(userId, "reader" + userId + "@example.com", "Мария");
        UUID eventId = UUID.randomUUID();
        send(Topics.ORDER_CREATED, orderId, orderCreated(eventId, orderId, userId));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(notificationsOf(userId)).isEqualTo(1));

        MvcTestResult foreign = mvc.post().uri("/api/notifications/{id}/read", eventId)
                .header("X-User-Id", userId + 100_000).exchange();
        MvcTestResult own = mvc.post().uri("/api/notifications/{id}/read", eventId)
                .header("X-User-Id", userId).exchange();
        MvcTestResult anonymous = mvc.get().uri("/api/notifications").header("X-Internal-Token", "wrong")
                .header("X-User-Id", userId).exchange();

        assertThat(foreign).hasStatus(404);
        assertThat(own).hasStatusOk().bodyJson().extractingPath("$.read").isEqualTo(true);
        assertThat(anonymous).hasStatus(401);
    }

    private void send(String topic, long orderId, Object event) {
        kafkaTemplate.send(topic, Long.toString(orderId), event).join();
    }

    private static void userExists(long userId, String email, String name) {
        USERS.stubFor(get(urlEqualTo("/internal/users/" + userId + "/contact"))
                .willReturn(okJson("{\"id\":" + userId + ",\"email\":\"" + email + "\",\"name\":\"" + name + "\"}")));
    }

    private static OrderCreatedEvent orderCreated(UUID eventId, long orderId, long userId) {
        return new OrderCreatedEvent(eventId, orderId, userId, 1L, new BigDecimal("640.00"), Instant.now(),
                List.of(), 1L, null, null, null, null);
    }

    private long notificationsOf(long userId) {
        return jdbcClient.sql("SELECT count(*) FROM notifications WHERE user_id = :userId")
                .param("userId", userId).query(Long.class).single();
    }

    private long notificationsOfOrder(long orderId) {
        return jdbcClient.sql("SELECT count(*) FROM notifications WHERE order_id = :orderId")
                .param("orderId", orderId).query(Long.class).single();
    }

    private List<String> typesOf(long userId) {
        return jdbcClient.sql("SELECT type FROM notifications WHERE user_id = :userId")
                .param("userId", userId).query(String.class).list();
    }

    private String mailStatusOf(long orderId) {
        return jdbcClient.sql("SELECT mail_status FROM notifications WHERE order_id = :orderId")
                .param("orderId", orderId).query(String.class).optional().orElse(null);
    }

    private int mailsTo(String email) {
        return mailSubjectsTo(email).size();
    }

    private List<String> mailSubjectsTo(String email) {
        String body = RestClient.create("http://" + mailpit.getHost() + ":"
                        + mailpit.getMappedPort(MailpitContainerConfig.API_PORT))
                .get().uri("/api/v1/search?query={query}", "to:" + email)
                .retrieve().body(String.class);
        return JsonPath.read(body, "$.messages[*].Subject");
    }
}
