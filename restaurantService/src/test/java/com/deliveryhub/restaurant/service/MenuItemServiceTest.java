package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.dto.MenuItemRequest;
import com.deliveryhub.restaurant.entity.MenuItem;
import com.deliveryhub.restaurant.entity.MenuSection;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mapper.MenuItemMapper;
import com.deliveryhub.restaurant.repository.MenuItemRepository;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MenuItemService")
class MenuItemServiceTest {

    private static final long RESTAURANT_ID = 1L;
    private static final long MENU_ITEM_ID = 10L;

    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private MenuItemMapper menuItemMapper;

    @InjectMocks
    private MenuItemService menuItemService;

    @Test
    @DisplayName("ищет блюдо только внутри указанного ресторана")
    void looksUpItemScopedToRestaurant() {
        MenuItem item = menuItem();
        when(menuItemRepository.findByIdAndRestaurantId(MENU_ITEM_ID, RESTAURANT_ID))
                .thenReturn(Optional.of(item));
        MenuItemDto dto = dto();
        when(menuItemMapper.toDto(item)).thenReturn(dto);

        assertThat(menuItemService.getMenuItemById(RESTAURANT_ID, MENU_ITEM_ID)).isSameAs(dto);
    }

    @Test
    @DisplayName("не отдаёт блюдо чужого ресторана")
    void rejectsItemFromAnotherRestaurant() {
        when(menuItemRepository.findByIdAndRestaurantId(MENU_ITEM_ID, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.getMenuItemById(999L, MENU_ITEM_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("was not found in restaurant 999");
    }

    @Test
    @DisplayName("не удаляет блюдо через чужой ресторан")
    void doesNotDeleteAcrossRestaurants() {
        when(menuItemRepository.findByIdAndRestaurantId(MENU_ITEM_ID, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.removeMenuItem(999L, MENU_ITEM_ID))
                .isInstanceOf(EntityNotFoundException.class);
        verify(menuItemRepository, never()).delete(any());
    }

    @Test
    @DisplayName("по умолчанию отдаёт только доступные позиции меню")
    void returnsOnlyAvailableByDefault() {
        when(menuItemRepository.findByRestaurantIdAndAvailableTrue(RESTAURANT_ID)).thenReturn(List.of(menuItem()));
        when(menuItemMapper.toDto(any())).thenReturn(dto());

        assertThat(menuItemService.getMenuItemsByRestaurant(RESTAURANT_ID, true)).hasSize(1);
        verify(menuItemRepository, never()).findByRestaurantId(RESTAURANT_ID);
    }

    @Test
    @DisplayName("привязывает новое блюдо к ресторану")
    void attachesNewItemToRestaurant() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> inv.getArgument(0));

        menuItemService.addMenuItemToRestaurant(RESTAURANT_ID, request(true));

        assertThat(restaurant.getMenuItems()).singleElement().satisfies(item -> {
            assertThat(item.getName()).isEqualTo("Pizza");
            assertThat(item.getRestaurant()).isSameAs(restaurant);
            assertThat(item.isAvailable()).isTrue();
        });
    }

    @Test
    @DisplayName("обновление может снять блюдо с продажи")
    void updateWithdrawsItemFromSale() {
        MenuItem item = menuItem();
        when(menuItemRepository.findByIdAndRestaurantId(MENU_ITEM_ID, RESTAURANT_ID))
                .thenReturn(Optional.of(item));

        menuItemService.updateMenuItem(RESTAURANT_ID, MENU_ITEM_ID, request(false));

        assertThat(item.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("бросает EntityNotFoundException при добавлении блюда в несуществующий ресторан")
    void failsWhenRestaurantMissing() {
        when(restaurantRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.addMenuItemToRestaurant(404L, request(true)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private static MenuItem menuItem() {
        return new Restaurant("Pizzeria", null, null)
                .addMenuItem("Pizza", null, new BigDecimal("12.50"), "pizza");
    }

    private static MenuItemRequest request(boolean available) {
        return new MenuItemRequest("Pizza", null, new BigDecimal("12.50"), "pizza", available);
    }

    private static MenuItemDto dto() {
        return new MenuItemDto(MENU_ITEM_ID, "Pizza", null, new BigDecimal("12.50"), "pizza", true, null, MenuSection.DISH, null, RESTAURANT_ID);
    }
}
