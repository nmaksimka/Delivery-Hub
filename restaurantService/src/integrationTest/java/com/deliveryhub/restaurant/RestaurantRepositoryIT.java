package com.deliveryhub.restaurant;

import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PostgresContainerConfig.class)
class RestaurantRepositoryIT {

    @Autowired
    private RestaurantRepository restaurantRepository;
    @Autowired
    private MenuItemRepository menuItemRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void findsOnlyActiveRestaurants() {
        Restaurant closed = new Restaurant("Closed", null, null);
        closed.deactivate();
        restaurantRepository.save(closed);
        restaurantRepository.save(new Restaurant("Open", null, null));

        assertThat(restaurantRepository.findByActiveTrue())
                .extracting(Restaurant::getName)
                .contains("Open")
                .doesNotContain("Closed");
    }

    @Test
    void savesMenuItemsTogetherWithRestaurant() {
        Restaurant restaurant = new Restaurant("Pizzeria", "Lenina 1", null);
        restaurant.addMenuItem("Margherita", "classic", new BigDecimal("450.00"), "pizza");
        restaurant.addMenuItem("Cola", null, new BigDecimal("120.00"), "drinks").withdrawFromSale();
        Long restaurantId = restaurantRepository.saveAndFlush(restaurant).getId();
        entityManager.clear();

        assertThat(menuItemRepository.findByRestaurantId(restaurantId)).hasSize(2);
        assertThat(menuItemRepository.findByRestaurantIdAndAvailableTrue(restaurantId))
                .extracting(MenuItem::getName)
                .containsExactly("Margherita");
    }

    @Test
    void doesNotFindMenuItemThroughAnotherRestaurant() {
        Restaurant owner = new Restaurant("Owner", null, null);
        MenuItem item = owner.addMenuItem("Soup", null, new BigDecimal("300.00"), null);
        restaurantRepository.saveAndFlush(owner);
        Restaurant stranger = restaurantRepository.saveAndFlush(new Restaurant("Stranger", null, null));

        assertThat(menuItemRepository.findByIdAndRestaurantId(item.getId(), owner.getId())).isPresent();
        assertThat(menuItemRepository.findByIdAndRestaurantId(item.getId(), stranger.getId())).isEmpty();
    }

    @Test
    void deletingRestaurantRemovesItsMenu() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);
        restaurant.addMenuItem("Margherita", null, new BigDecimal("450.00"), "pizza");
        Long restaurantId = restaurantRepository.saveAndFlush(restaurant).getId();
        entityManager.clear();

        restaurantRepository.deleteById(restaurantId);
        restaurantRepository.flush();

        assertThat(menuItemRepository.findByRestaurantId(restaurantId)).isEmpty();
    }
}
