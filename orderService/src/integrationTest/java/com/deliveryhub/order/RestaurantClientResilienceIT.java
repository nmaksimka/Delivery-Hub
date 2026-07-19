package com.deliveryhub.order;

import com.deliveryhub.contracts.pricing.ItemStatus;
import com.deliveryhub.contracts.pricing.MenuPricingGrpc;
import com.deliveryhub.contracts.pricing.QuoteRequest;
import com.deliveryhub.contracts.pricing.QuoteResponse;
import com.deliveryhub.contracts.pricing.QuotedItem;
import com.deliveryhub.order.client.RestaurantCatalog;
import com.deliveryhub.order.client.RestaurantCircuitOpenException;
import com.deliveryhub.order.client.RestaurantServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, KafkaContainerConfig.class})
class RestaurantClientResilienceIT {

    private static final AtomicInteger CALLS = new AtomicInteger();
    private static volatile Consumer<StreamObserver<QuoteResponse>> behaviour = answer -> {
    };
    private static final Server RESTAURANT_SERVICE = startRestaurantService();

    @Autowired
    private RestaurantCatalog restaurantCatalog;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private MockMvcTester mvc;

    @DynamicPropertySource
    static void restaurantServiceAddress(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.client.channels.restaurant.address",
                () -> "static://localhost:" + RESTAURANT_SERVICE.getPort());
        registry.add("app.restaurant-grpc.deadline", () -> "400ms");
        registry.add("resilience4j.retry.instances.restaurantService.wait-duration", () -> "20ms");
    }

    @AfterAll
    static void stopRestaurantService() {
        RESTAURANT_SERVICE.shutdownNow();
    }

    @BeforeEach
    void resetState() {
        CALLS.set(0);
        behaviour = answer -> reply(answer, pizzeria("12.50"));
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void readsQuoteFromRestaurantServiceOverNetwork() {
        assertThat(restaurantCatalog.quote(1L, List.of(10L)).item(10L))
                .hasValueSatisfying(pizza -> assertThat(pizza.price()).isEqualByComparingTo("12.50"));
        assertThat(CALLS).hasValue(1);
    }

    @Test
    void totalsOrderByPriceFromRestaurantServiceNotFromClient() {
        MvcTestResult result = placeOrder();

        assertThat(result).hasStatus(201).bodyJson().extractingPath("$.totalAmount").isEqualTo(25.0);
    }

    @Test
    void repeatsCallAfterUnavailableUntilItSucceeds() {
        AtomicInteger attempt = new AtomicInteger();
        behaviour = answer -> {
            if (attempt.incrementAndGet() < 3) {
                answer.onError(Status.UNAVAILABLE.asRuntimeException());
            } else {
                reply(answer, pizzeria("12.50"));
            }
        };

        assertThat(restaurantCatalog.quote(1L, List.of(10L)).restaurant().active()).isTrue();
        assertThat(CALLS).hasValue(3);
    }

    @Test
    void missingRestaurantIsNeitherRetriedNorCountedAsFailure() {
        behaviour = answer -> answer.onError(Status.NOT_FOUND.asRuntimeException());

        for (int call = 0; call < 6; call++) {
            assertThatThrownBy(() -> restaurantCatalog.quote(1L, List.of(10L)))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        assertThat(CALLS).hasValue(6);
        assertThat(circuitBreakerRegistry.getAllCircuitBreakers())
                .allSatisfy(breaker -> assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED));
    }

    @Test
    void answersServiceUnavailableProblemWhenRestaurantServiceStaysDown() {
        behaviour = answer -> answer.onError(Status.UNAVAILABLE.asRuntimeException());

        MvcTestResult result = placeOrder();

        assertThat(result).hasStatus(503).hasHeader(HttpHeaders.RETRY_AFTER, "10");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Restaurant service unavailable");
        assertThat(CALLS).hasValue(3);
    }

    @Test
    void givesUpOnAnswerSlowerThanDeadlineWithoutRepeating() {
        behaviour = answer -> {
            try {
                Thread.sleep(900);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        assertThatThrownBy(() -> restaurantCatalog.quote(1L, List.of(10L)))
                .isExactlyInstanceOf(RestaurantServiceUnavailableException.class);
        assertThat(CALLS).hasValue(1);
    }

    @Test
    void opensCircuitAfterRepeatedFailuresAndStopsCallingRestaurantService() {
        behaviour = answer -> answer.onError(Status.INTERNAL.asRuntimeException());
        for (int call = 0; call < 5; call++) {
            assertThatThrownBy(() -> restaurantCatalog.quote(1L, List.of(10L)))
                    .isExactlyInstanceOf(RestaurantServiceUnavailableException.class);
        }
        int callsBeforeOpenCircuit = CALLS.get();

        assertThatThrownBy(() -> restaurantCatalog.quote(1L, List.of(10L)))
                .isInstanceOf(RestaurantCircuitOpenException.class);

        assertThat(callsBeforeOpenCircuit).isEqualTo(5);
        assertThat(CALLS).hasValue(callsBeforeOpenCircuit);
    }

    private MvcTestResult placeOrder() {
        return mvc.post().uri("/api/orders")
                .header("X-User-Id", "42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"restaurantId": 1, "orderItemsRequest": [{"menuItemId": 10, "quantity": 2}]}
                        """)
                .exchange();
    }

    private static QuoteResponse pizzeria(String price) {
        return QuoteResponse.newBuilder().setRestaurantId(1L).setRestaurantName("Pizzeria").setRestaurantActive(true)
                .addItems(QuotedItem.newBuilder().setMenuItemId(10L).setStatus(ItemStatus.ITEM_STATUS_AVAILABLE)
                        .setName("Pizza").setPrice(price))
                .build();
    }

    private static void reply(StreamObserver<QuoteResponse> answer, QuoteResponse response) {
        answer.onNext(response);
        answer.onCompleted();
    }

    private static Server startRestaurantService() {
        try {
            return NettyServerBuilder.forPort(0).addService(new MenuPricingGrpc.MenuPricingImplBase() {
                @Override
                public void quote(QuoteRequest request, StreamObserver<QuoteResponse> responseObserver) {
                    CALLS.incrementAndGet();
                    behaviour.accept(responseObserver);
                }
            }).build().start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
