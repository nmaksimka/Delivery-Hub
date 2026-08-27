package com.deliveryhub.search.catalog;

import com.deliveryhub.contracts.events.CatalogDishPayload;
import com.deliveryhub.contracts.events.RestaurantCatalogChangedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogDocumentTest {

    private static final Instant CHANGED_AT = Instant.parse("2026-07-10T12:00:00Z");

    @Test
    void dishCarriesRestaurantNameAndActivity() {
        RestaurantCatalogChangedEvent event = event(false, false);

        CatalogDocument dish = CatalogDocument.dishOf(event, event.getDishes().getFirst());

        assertThat(dish.key()).isEqualTo("dish-7");
        assertThat(dish.restaurantName()).isEqualTo("Томские щи");
        assertThat(dish.active()).isFalse();
        assertThat(dish.changedAt()).isEqualTo(CHANGED_AT.toEpochMilli());
    }

    @Test
    void removedRestaurantBecomesInactiveTombstone() {
        CatalogDocument document = CatalogDocument.restaurantOf(event(true, true));

        assertThat(document.kind()).isEqualTo(CatalogDocument.REMOVED);
        assertThat(document.active()).isFalse();
        assertThat(document.key()).isEqualTo("restaurant-3");
    }

    @Test
    void restaurantDocumentUsesRestaurantKey() {
        CatalogDocument document = CatalogDocument.restaurantOf(event(false, true));

        assertThat(document.kind()).isEqualTo(CatalogDocument.RESTAURANT);
        assertThat(document.key()).isEqualTo(CatalogDocument.restaurantKey(3));
        assertThat(document.active()).isTrue();
    }

    private static RestaurantCatalogChangedEvent event(boolean removed, boolean active) {
        return RestaurantCatalogChangedEvent.newBuilder()
                .setEventId(UUID.randomUUID())
                .setRestaurantId(3L)
                .setRemoved(removed)
                .setName("Томские щи")
                .setActive(active)
                .setDishes(List.of(CatalogDishPayload.newBuilder()
                        .setMenuItemId(7L)
                        .setName("Щи суточные")
                        .setSection("DISH")
                        .setPrice(new BigDecimal("320.00"))
                        .setAvailable(true)
                        .build()))
                .setChangedAt(CHANGED_AT)
                .build();
    }
}
