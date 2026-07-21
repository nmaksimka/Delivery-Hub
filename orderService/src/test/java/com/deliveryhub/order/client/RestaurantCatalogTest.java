package com.deliveryhub.order.client;

import com.deliveryhub.contracts.pricing.ItemStatus;
import com.deliveryhub.contracts.pricing.MenuPricingGrpc;
import com.deliveryhub.contracts.pricing.QuoteRequest;
import com.deliveryhub.contracts.pricing.QuoteResponse;
import com.deliveryhub.contracts.pricing.QuotedItem;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestaurantCatalogTest {

    private final AtomicInteger calls = new AtomicInteger();
    private volatile Consumer<StreamObserver<QuoteResponse>> behaviour = answer -> {
    };
    private Server server;
    private ManagedChannel channel;
    private RestaurantCatalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        String name = UUID.randomUUID().toString();
        server = InProcessServerBuilder.forName(name).executor(Runnable::run)
                .addService(new MenuPricingGrpc.MenuPricingImplBase() {
                    @Override
                    public void quote(QuoteRequest request, StreamObserver<QuoteResponse> responseObserver) {
                        calls.incrementAndGet();
                        behaviour.accept(responseObserver);
                    }
                }).build().start();
        channel = InProcessChannelBuilder.forName(name).build();

        CircuitBreakerConfig breaker = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .ignoreExceptions(EntityNotFoundException.class)
                .build();
        RetryConfig retry = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(RestaurantUnreachableException.class)
                .build();
        catalog = new RestaurantCatalog(MenuPricingGrpc.newBlockingStub(channel),
                new RestaurantGrpcProperties(Duration.ofMillis(200)),
                CircuitBreakerRegistry.of(java.util.Map.of(RestaurantCatalog.RESILIENCE_NAME, breaker)),
                RetryRegistry.of(retry));
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void keepsPricedItemsAndDropsMissingOnes() {
        behaviour = answer -> reply(answer, QuoteResponse.newBuilder()
                .setRestaurantId(1L).setRestaurantName("Pizzeria").setRestaurantActive(true)
                .addItems(item(10L, ItemStatus.ITEM_STATUS_AVAILABLE, "Pizza", "12.50"))
                .addItems(item(11L, ItemStatus.ITEM_STATUS_UNAVAILABLE, "Soup", "5.00"))
                .addItems(item(12L, ItemStatus.ITEM_STATUS_NOT_FOUND, "", ""))
                .addItems(item(13L, ItemStatus.ITEM_STATUS_OTHER_RESTAURANT, "", ""))
                .build());

        MenuQuote quote = catalog.quote(1L, List.of(10L, 11L, 12L, 13L));

        assertThat(quote.restaurant().active()).isTrue();
        assertThat(quote.item(10L)).hasValueSatisfying(pizza -> {
            assertThat(pizza.price()).isEqualByComparingTo("12.50");
            assertThat(pizza.available()).isTrue();
        });
        assertThat(quote.item(11L)).hasValueSatisfying(soup -> assertThat(soup.available()).isFalse());
        assertThat(quote.item(12L)).isEmpty();
        assertThat(quote.item(13L)).isEmpty();
    }

    @Test
    void readsClosedRestaurantWithItsHours() {
        behaviour = answer -> reply(answer, QuoteResponse.newBuilder()
                .setRestaurantId(1L).setRestaurantName("Pizzeria").setRestaurantActive(true)
                .setRestaurantClosed(true).setOpensAt("10:00").setClosesAt("22:00").setTimeZone("Asia/Tomsk")
                .build());

        MenuQuote quote = catalog.quote(1L, List.of(10L));

        assertThat(quote.restaurant().closed()).isTrue();
        assertThat(quote.restaurant().workingHours()).isEqualTo("10:00-22:00 Asia/Tomsk");
    }

    @Test
    void answerWithoutHoursMeansOpenRestaurant() {
        behaviour = answer -> reply(answer,
                QuoteResponse.newBuilder().setRestaurantId(1L).setRestaurantActive(true).build());

        MenuQuote quote = catalog.quote(1L, List.of(10L));

        assertThat(quote.restaurant().closed()).isFalse();
        assertThat(quote.restaurant().workingHours()).isEmpty();
    }

    @Test
    void turnsNotFoundIntoMissingRestaurantWithoutRetry() {
        behaviour = answer -> answer.onError(Status.NOT_FOUND.asRuntimeException());

        assertThatThrownBy(() -> catalog.quote(1L, List.of(10L))).isInstanceOf(EntityNotFoundException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void repeatsCallWhenServiceIsUnavailable() {
        AtomicInteger attempt = new AtomicInteger();
        behaviour = answer -> {
            if (attempt.incrementAndGet() < 3) {
                answer.onError(Status.UNAVAILABLE.asRuntimeException());
            } else {
                reply(answer, QuoteResponse.newBuilder().setRestaurantId(1L).setRestaurantActive(true).build());
            }
        };

        assertThat(catalog.quote(1L, List.of(10L)).restaurant().active()).isTrue();
        assertThat(calls).hasValue(3);
    }

    @Test
    void givesUpAfterLastAttempt() {
        behaviour = answer -> answer.onError(Status.UNAVAILABLE.asRuntimeException());

        assertThatThrownBy(() -> catalog.quote(1L, List.of(10L)))
                .isExactlyInstanceOf(RestaurantUnreachableException.class);
        assertThat(calls).hasValue(3);
    }

    @Test
    void doesNotRepeatOnServerError() {
        behaviour = answer -> answer.onError(Status.INTERNAL.asRuntimeException());

        assertThatThrownBy(() -> catalog.quote(1L, List.of(10L)))
                .isExactlyInstanceOf(RestaurantServiceUnavailableException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void doesNotRepeatWhenDeadlineIsExceeded() {
        behaviour = answer -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        assertThatThrownBy(() -> catalog.quote(1L, List.of(10L)))
                .isExactlyInstanceOf(RestaurantServiceUnavailableException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void stopsCallingServiceWhenCircuitIsOpen() {
        behaviour = answer -> answer.onError(Status.INTERNAL.asRuntimeException());
        for (int call = 0; call < 4; call++) {
            assertThatThrownBy(() -> catalog.quote(1L, List.of(10L)))
                    .isExactlyInstanceOf(RestaurantServiceUnavailableException.class);
        }

        assertThatThrownBy(() -> catalog.quote(1L, List.of(10L)))
                .isInstanceOf(RestaurantCircuitOpenException.class);
        assertThat(calls).hasValue(4);
    }

    private static void reply(StreamObserver<QuoteResponse> answer, QuoteResponse response) {
        answer.onNext(response);
        answer.onCompleted();
    }

    private static QuotedItem item(long id, ItemStatus status, String name, String price) {
        return QuotedItem.newBuilder().setMenuItemId(id).setStatus(status).setName(name).setPrice(price).build();
    }
}
