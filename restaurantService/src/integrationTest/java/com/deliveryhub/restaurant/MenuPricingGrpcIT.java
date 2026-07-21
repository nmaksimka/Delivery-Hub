package com.deliveryhub.restaurant;

import com.deliveryhub.contracts.pricing.ItemStatus;
import com.deliveryhub.contracts.pricing.MenuPricingGrpc;
import com.deliveryhub.contracts.pricing.QuoteRequest;
import com.deliveryhub.contracts.pricing.QuoteResponse;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.NettyChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.grpc.server.lifecycle.GrpcServerStartedEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({PostgresContainerConfig.class, MenuPricingGrpcIT.GrpcPortHolder.class})
class MenuPricingGrpcIT {

    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
    private GrpcPortHolder grpcPort;

    private ManagedChannel channel;

    @BeforeEach
    void connect() {
        channel = NettyChannelBuilder.forAddress("localhost", grpcPort.value.get()).usePlaintext().build();
    }

    @AfterEach
    void disconnect() {
        channel.shutdownNow();
    }

    @Test
    void quotesRealMenuOverNetwork() {
        Restaurant draft = new Restaurant("Grpc Bistro", "Lenina 5", "+7111");
        draft.addMenuItem("Soup", null, new BigDecimal("250.00"), null);
        draft.addMenuItem("Stopped", null, new BigDecimal("300.00"), null).withdrawFromSale();
        Restaurant restaurant = restaurantRepository.saveAndFlush(draft);
        Restaurant otherDraft = new Restaurant("Other", "Lenina 6", "+7222");
        otherDraft.addMenuItem("Foreign", null, new BigDecimal("10.00"), null);
        Restaurant other = restaurantRepository.saveAndFlush(otherDraft);
        Long soupId = idOf(restaurant, "Soup");
        Long stoppedId = idOf(restaurant, "Stopped");
        Long foreignId = idOf(other, "Foreign");

        QuoteResponse response = MenuPricingGrpc.newBlockingStub(channel).quote(QuoteRequest.newBuilder()
                .setRestaurantId(restaurant.getId())
                .addMenuItemIds(soupId)
                .addMenuItemIds(stoppedId)
                .addMenuItemIds(foreignId)
                .addMenuItemIds(987654321L)
                .build());

        assertThat(response.getRestaurantName()).isEqualTo("Grpc Bistro");
        assertThat(response.getItemsList()).extracting(item -> item.getStatus())
                .containsExactly(ItemStatus.ITEM_STATUS_AVAILABLE, ItemStatus.ITEM_STATUS_UNAVAILABLE,
                        ItemStatus.ITEM_STATUS_OTHER_RESTAURANT, ItemStatus.ITEM_STATUS_NOT_FOUND);
        assertThat(new BigDecimal(response.getItems(0).getPrice())).isEqualByComparingTo("250.00");
    }

    @Test
    void reportsRestaurantClosedOutsideItsHoursInTomsk() {
        LocalTime nowInTomsk = LocalTime.now(ZoneId.of("Asia/Tomsk")).truncatedTo(ChronoUnit.MINUTES);
        Restaurant closedDraft = new Restaurant("Opens later", "Lenina 7", null);
        closedDraft.changeWorkingHours(nowInTomsk.plusHours(2), nowInTomsk.plusHours(3));
        closedDraft.addMenuItem("Soup", null, new BigDecimal("250.00"), null);
        Restaurant closed = restaurantRepository.saveAndFlush(closedDraft);
        Restaurant openDraft = new Restaurant("Open now", "Lenina 8", null);
        openDraft.changeWorkingHours(nowInTomsk.minusHours(1), nowInTomsk.plusHours(1));
        openDraft.addMenuItem("Soup", null, new BigDecimal("250.00"), null);
        Restaurant open = restaurantRepository.saveAndFlush(openDraft);
        MenuPricingGrpc.MenuPricingBlockingStub stub = MenuPricingGrpc.newBlockingStub(channel);

        QuoteResponse closedQuote = stub.quote(QuoteRequest.newBuilder()
                .setRestaurantId(closed.getId()).addMenuItemIds(idOf(closed, "Soup")).build());
        QuoteResponse openQuote = stub.quote(QuoteRequest.newBuilder()
                .setRestaurantId(open.getId()).addMenuItemIds(idOf(open, "Soup")).build());

        assertThat(closedQuote.getRestaurantClosed()).isTrue();
        assertThat(closedQuote.getOpensAt()).isEqualTo(nowInTomsk.plusHours(2).toString());
        assertThat(closedQuote.getItems(0).getStatus()).isEqualTo(ItemStatus.ITEM_STATUS_AVAILABLE);
        assertThat(openQuote.getRestaurantClosed()).isFalse();
    }

    @Test
    void answersNotFoundForUnknownRestaurant() {
        MenuPricingGrpc.MenuPricingBlockingStub stub = MenuPricingGrpc.newBlockingStub(channel);
        QuoteRequest request = QuoteRequest.newBuilder().setRestaurantId(987654321L).addMenuItemIds(1L).build();

        assertThatThrownBy(() -> stub.quote(request))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    void reportsServingInGrpcHealthCheck() {
        HealthCheckResponse response = HealthGrpc.newBlockingStub(channel)
                .check(HealthCheckRequest.getDefaultInstance());

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
    }

    private static Long idOf(Restaurant restaurant, String name) {
        return restaurant.getMenuItems().stream()
                .filter(item -> item.getName().equals(name)).findFirst().orElseThrow().getId();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Component
    static class GrpcPortHolder {

        final AtomicInteger value = new AtomicInteger();

        @EventListener
        void remember(GrpcServerStartedEvent event) {
            value.set(event.getPort());
        }
    }
}
