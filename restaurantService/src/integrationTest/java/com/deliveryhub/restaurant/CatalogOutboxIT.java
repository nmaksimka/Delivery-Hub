package com.deliveryhub.restaurant;

import com.deliveryhub.contracts.EventPayloads;
import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.RestaurantCatalogChangedEvent;
import com.deliveryhub.restaurant.dto.MenuItemRequest;
import com.deliveryhub.restaurant.dto.RestaurantRequest;
import com.deliveryhub.restaurant.outbox.OutboxEvent;
import com.deliveryhub.restaurant.outbox.OutboxEventRepository;
import com.deliveryhub.restaurant.service.MenuItemService;
import com.deliveryhub.restaurant.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({PostgresContainerConfig.class, GatewayTokenConfig.class})
class CatalogOutboxIT {

    @Autowired
    private RestaurantService restaurantService;
    @Autowired
    private MenuItemService menuItemService;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void everyCatalogChangeLeavesFullSnapshotInOutbox() {
        Long id = restaurantService.createRestaurant(
                new RestaurantRequest("Сибирские пельмени", "Ленина 1", null, null, null, true, null, null)).id();
        menuItemService.addMenuItemToRestaurant(id,
                new MenuItemRequest("Пельмени с бульоном", "Домашние", new BigDecimal("390.00"), "Горячее", true));

        List<RestaurantCatalogChangedEvent> events = eventsOf(id);

        assertThat(events).hasSize(2);
        RestaurantCatalogChangedEvent last = events.getLast();
        assertThat(last.getRemoved()).isFalse();
        assertThat(last.getName()).isEqualTo("Сибирские пельмени");
        assertThat(last.getDishes()).singleElement()
                .satisfies(dish -> {
                    assertThat(dish.getName()).isEqualTo("Пельмени с бульоном");
                    assertThat(dish.getPrice()).isEqualByComparingTo("390.00");
                });
    }

    @Test
    void removedRestaurantProducesTombstone() {
        Long id = restaurantService.createRestaurant(
                new RestaurantRequest("Закрываемся", null, null, null, null, true, null, null)).id();

        restaurantService.removeRestaurant(id);

        assertThat(eventsOf(id).getLast().getRemoved()).isTrue();
    }

    private List<RestaurantCatalogChangedEvent> eventsOf(Long restaurantId) {
        return outboxEventRepository.findByAggregateIdOrderByCreatedAt(restaurantId.toString()).stream()
                .map(OutboxEvent::getPayload)
                .map(payload -> (RestaurantCatalogChangedEvent) EventPayloads.decode(
                        Topics.RESTAURANT_CATALOG_CHANGED, payload))
                .toList();
    }
}
