package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.entity.MenuItem;
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
        MenuItemDto dto = new MenuItemDto();
        when(menuItemMapper.toDto(item)).thenReturn(dto);

        assertThat(menuItemService.getMenuItemById(RESTAURANT_ID, MENU_ITEM_ID)).isSameAs(dto);
    }

    @Test
    @DisplayName("не отдаёт блюдо чужого ресторана")
    void rejectsItemFromAnotherRestaurant() {
        when(menuItemRepository.findByIdAndRestaurantId(MENU_ITEM_ID, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.getMenuItemById(999L, MENU_ITEM_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("was not found in restaurant 999");
    }

    @Test
    @DisplayName("не удаляет блюдо через чужой ресторан")
    void doesNotDeleteAcrossRestaurants() {
        when(menuItemRepository.findByIdAndRestaurantId(MENU_ITEM_ID, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.removeMenuItem(999L, MENU_ITEM_ID))
                .isInstanceOf(EntityNotFoundException.class);

        verify(menuItemRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("по умолчанию отдаёт только доступные позиции меню")
    void returnsOnlyAvailableByDefault() {
        when(menuItemRepository.findByRestaurantIdAndAvailableTrue(RESTAURANT_ID))
                .thenReturn(List.of(menuItem()));
        when(menuItemMapper.toDto(org.mockito.ArgumentMatchers.any())).thenReturn(new MenuItemDto());

        assertThat(menuItemService.getMenuItemsByRestaurant(RESTAURANT_ID, true)).hasSize(1);
        verify(menuItemRepository, never()).findByRestaurantId(RESTAURANT_ID);
    }

    @Test
    @DisplayName("привязывает новое блюдо к ресторану")
    void attachesNewItemToRestaurant() {
        Restaurant restaurant = Restaurant.builder().id(RESTAURANT_ID).name("Pizzeria").active(true).build();
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));

        MenuItem newItem = MenuItem.builder().name("Pizza").price(BigDecimal.TEN).build();
        when(menuItemMapper.toEntity(org.mockito.ArgumentMatchers.any())).thenReturn(newItem);
        when(menuItemRepository.save(newItem)).thenReturn(newItem);
        when(menuItemMapper.toDto(newItem)).thenReturn(new MenuItemDto());

        menuItemService.addMenuItemToRestaurant(RESTAURANT_ID, new MenuItemDto());

        assertThat(newItem.getRestaurant()).isSameAs(restaurant);
        assertThat(restaurant.getMenuItems()).containsExactly(newItem);
    }

    @Test
    @DisplayName("бросает EntityNotFoundException при добавлении блюда в несуществующий ресторан")
    void failsWhenRestaurantMissing() {
        when(restaurantRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuItemService.addMenuItemToRestaurant(404L, new MenuItemDto()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private static MenuItem menuItem() {
        return MenuItem.builder()
                .id(MENU_ITEM_ID)
                .name("Pizza")
                .price(new BigDecimal("12.50"))
                .available(true)
                .build();
    }
}
