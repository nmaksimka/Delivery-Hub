package com.deliveryhub.restaurant.outbox;

import com.deliveryhub.contracts.Topics;
import com.deliveryhub.contracts.events.CatalogDishPayload;
import com.deliveryhub.contracts.events.RestaurantCatalogChangedEvent;
import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class CatalogChanges {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final Outbox outbox;
    private final Clock clock;

    public CatalogChanges(RestaurantRepository restaurantRepository, MenuItemRepository menuItemRepository,
                          Outbox outbox, Clock clock) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.outbox = outbox;
        this.clock = clock;
    }

    public void restaurantChanged(Long restaurantId) {
        pendingRestaurants().add(restaurantId);
    }

    public void restaurantRemoved(Long restaurantId, String name) {
        pendingRestaurants().remove(restaurantId);
        UUID eventId = UUID.randomUUID();
        outbox.append(Topics.RESTAURANT_CATALOG_CHANGED, restaurantId, eventId,
                RestaurantCatalogChangedEvent.newBuilder()
                        .setEventId(eventId)
                        .setRestaurantId(restaurantId)
                        .setRemoved(true)
                        .setName(name)
                        .setActive(false)
                        .setDishes(List.of())
                        .setChangedAt(clock.instant())
                        .build());
    }

    private Set<Long> pendingRestaurants() {
        @SuppressWarnings("unchecked")
        Set<Long> pending = (Set<Long>) TransactionSynchronizationManager.getResource(this);
        if (pending == null) {
            Set<Long> created = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(this, created);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    created.forEach(CatalogChanges.this::appendSnapshot);
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(CatalogChanges.this);
                }
            });
            return created;
        }
        return pending;
    }

    private void appendSnapshot(Long restaurantId) {
        restaurantRepository.findById(restaurantId).ifPresent(restaurant -> {
            UUID eventId = UUID.randomUUID();
            outbox.append(Topics.RESTAURANT_CATALOG_CHANGED, restaurantId, eventId,
                    snapshot(eventId, restaurant, menuItemRepository.findByRestaurantId(restaurantId)));
        });
    }

    private RestaurantCatalogChangedEvent snapshot(UUID eventId, Restaurant restaurant, List<MenuItem> items) {
        return RestaurantCatalogChangedEvent.newBuilder()
                .setEventId(eventId)
                .setRestaurantId(restaurant.getId())
                .setRemoved(false)
                .setName(restaurant.getName())
                .setCuisine(restaurant.getCuisine())
                .setAddress(restaurant.getAddress())
                .setActive(restaurant.isActive())
                .setDishes(items.stream().map(CatalogChanges::dish).toList())
                .setChangedAt(clock.instant())
                .build();
    }

    private static CatalogDishPayload dish(MenuItem item) {
        return CatalogDishPayload.newBuilder()
                .setMenuItemId(item.getId())
                .setName(item.getName())
                .setDescription(item.getDescription())
                .setCategory(item.getCategory())
                .setSection(item.getSection().name())
                .setPrice(item.getPrice())
                .setAvailable(item.isAvailable())
                .build();
    }
}
