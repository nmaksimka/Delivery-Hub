package com.deliveryhub.restaurant.grpc;

import com.deliveryhub.contracts.pricing.ItemStatus;
import com.deliveryhub.contracts.pricing.MenuPricingGrpc;
import com.deliveryhub.contracts.pricing.QuoteRequest;
import com.deliveryhub.contracts.pricing.QuoteResponse;
import com.deliveryhub.contracts.pricing.QuotedItem;
import com.deliveryhub.restaurant.config.WorkingHoursProperties;
import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuPricingGrpcServiceTest {

    private static final long RESTAURANT_ID = 1L;
    private static final Clock NOON_IN_TOMSK = Clock.fixed(Instant.parse("2026-07-27T05:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private MenuItemRepository menuItemRepository;

    private Server server;
    private ManagedChannel channel;
    private MenuPricingGrpc.MenuPricingBlockingStub stub;

    @BeforeEach
    void startServer() throws Exception {
        String name = UUID.randomUUID().toString();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new MenuPricingGrpcService(restaurantRepository, menuItemRepository,
                        new WorkingHoursProperties(ZoneId.of("Asia/Tomsk")), NOON_IN_TOMSK))
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = MenuPricingGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stopServer() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void quotesAvailableItemWithServerSidePrice() {
        Restaurant restaurant = restaurant(RESTAURANT_ID, true);
        MenuItem pizza = menuItem(restaurant, 10L, "Pizza", "12.50", true);
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findAllById(java.util.Set.of(10L))).thenReturn(List.of(pizza));

        QuoteResponse response = stub.quote(request(10L));

        assertThat(response.getRestaurantName()).isEqualTo("Pizzeria");
        assertThat(response.getRestaurantActive()).isTrue();
        assertThat(response.getItemsList()).singleElement().satisfies(item -> {
            assertThat(item.getStatus()).isEqualTo(ItemStatus.ITEM_STATUS_AVAILABLE);
            assertThat(item.getName()).isEqualTo("Pizza");
            assertThat(new BigDecimal(item.getPrice())).isEqualByComparingTo("12.50");
        });
    }

    @Test
    void marksWithdrawnItemUnavailableButStillPrices() {
        Restaurant restaurant = restaurant(RESTAURANT_ID, true);
        MenuItem soup = menuItem(restaurant, 11L, "Soup", "5.00", false);
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findAllById(java.util.Set.of(11L))).thenReturn(List.of(soup));

        QuotedItem item = stub.quote(request(11L)).getItems(0);

        assertThat(item.getStatus()).isEqualTo(ItemStatus.ITEM_STATUS_UNAVAILABLE);
        assertThat(item.getPrice()).isEqualTo("5.00");
    }

    @Test
    void reportsMissingItemAndItemOfAnotherRestaurant() {
        Restaurant restaurant = restaurant(RESTAURANT_ID, true);
        Restaurant other = restaurant(2L, true);
        MenuItem foreign = menuItem(other, 20L, "Sushi", "9.00", true);
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findAllById(java.util.Set.of(20L, 99L))).thenReturn(List.of(foreign));

        QuoteResponse response = stub.quote(request(20L, 99L));

        assertThat(response.getItemsList()).extracting(QuotedItem::getStatus)
                .containsExactly(ItemStatus.ITEM_STATUS_OTHER_RESTAURANT, ItemStatus.ITEM_STATUS_NOT_FOUND);
        assertThat(response.getItems(0).getPrice()).isEmpty();
    }

    @Test
    void reportsInactiveRestaurant() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant(RESTAURANT_ID, false)));
        when(menuItemRepository.findAllById(java.util.Set.of(10L))).thenReturn(List.of());

        assertThat(stub.quote(request(10L)).getRestaurantActive()).isFalse();
    }

    @Test
    void reportsOpenRestaurantWithItsHours() {
        Restaurant restaurant = restaurant(RESTAURANT_ID, true);
        restaurant.changeWorkingHours(LocalTime.of(10, 0), LocalTime.of(22, 0));
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findAllById(java.util.Set.of(10L))).thenReturn(List.of());

        QuoteResponse response = stub.quote(request(10L));

        assertThat(response.getRestaurantClosed()).isFalse();
        assertThat(response.getOpensAt()).isEqualTo("10:00");
        assertThat(response.getClosesAt()).isEqualTo("22:00");
    }

    @Test
    void reportsClosedRestaurantByTomskTimeNotByUtc() {
        Restaurant restaurant = restaurant(RESTAURANT_ID, true);
        restaurant.changeWorkingHours(LocalTime.of(4, 0), LocalTime.of(6, 0));
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findAllById(java.util.Set.of(10L))).thenReturn(List.of());

        QuoteResponse response = stub.quote(request(10L));

        assertThat(response.getRestaurantClosed()).isTrue();
        assertThat(response.getRestaurantActive()).isTrue();
        assertThat(response.getOpensAt()).isEqualTo("04:00");
        assertThat(response.getClosesAt()).isEqualTo("06:00");
    }

    @Test
    void restaurantWithoutHoursIsNeverClosed() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant(RESTAURANT_ID, true)));
        when(menuItemRepository.findAllById(java.util.Set.of(10L))).thenReturn(List.of());

        QuoteResponse response = stub.quote(request(10L));

        assertThat(response.getRestaurantClosed()).isFalse();
        assertThat(response.getOpensAt()).isEmpty();
    }

    @Test
    void answersNotFoundForUnknownRestaurant() {
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stub.quote(request(10L)))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    void rejectsEmptyAndOversizedRequests() {
        verifyNoInteractions(restaurantRepository, menuItemRepository);
        long[] tooMany = LongStream.rangeClosed(1, MenuPricingGrpcService.MAX_ITEMS_PER_QUOTE + 1).toArray();

        assertThatThrownBy(() -> stub.quote(request()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        assertThatThrownBy(() -> stub.quote(request(tooMany)))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    private static QuoteRequest request(long... menuItemIds) {
        QuoteRequest.Builder request = QuoteRequest.newBuilder().setRestaurantId(RESTAURANT_ID);
        for (long id : menuItemIds) {
            request.addMenuItemIds(id);
        }
        return request.build();
    }

    private static Restaurant restaurant(long id, boolean active) {
        Restaurant restaurant = new Restaurant("Pizzeria", "Lenina 1", "+7000");
        ReflectionTestUtils.setField(restaurant, "id", id);
        ReflectionTestUtils.setField(restaurant, "active", active);
        return restaurant;
    }

    private static MenuItem menuItem(Restaurant restaurant, long id, String name, String price, boolean available) {
        MenuItem item = restaurant.addMenuItem(name, null, new BigDecimal(price), null);
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "available", available);
        return item;
    }
}
