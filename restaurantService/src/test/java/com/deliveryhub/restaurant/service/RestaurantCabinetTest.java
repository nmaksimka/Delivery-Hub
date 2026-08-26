package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.MenuItemDto;
import com.deliveryhub.restaurant.dto.MenuItemRequest;
import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.dto.WorkingHoursRequest;
import com.deliveryhub.restaurant.entity.MenuSection;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mapper.RestaurantMapper;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantCabinetTest {

    private static final long OWNER_ID = 7L;
    private static final long STRANGER_ID = 8L;
    private static final long RESTAURANT_ID = 1L;
    private static final long MENU_ITEM_ID = 10L;
    private static final byte[] IMAGE = {1, 2, 3};

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private RestaurantMapper restaurantMapper;
    @Mock
    private RestaurantService restaurantService;
    @Mock
    private MenuItemService menuItemService;

    @InjectMocks
    private RestaurantCabinet cabinet;

    @Test
    void listsOnlyRestaurantsOfOwner() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);
        when(restaurantRepository.findByOwnerIdOrderByName(OWNER_ID)).thenReturn(List.of(restaurant));
        when(restaurantMapper.toDto(restaurant)).thenReturn(restaurantDto());

        assertThat(cabinet.restaurantsOf(OWNER_ID)).containsExactly(restaurantDto());
    }

    @Test
    void showsOwnRestaurant() {
        Restaurant restaurant = new Restaurant("Pizzeria", null, null);
        when(restaurantRepository.findByIdAndOwnerId(RESTAURANT_ID, OWNER_ID)).thenReturn(Optional.of(restaurant));
        when(restaurantMapper.toDto(restaurant)).thenReturn(restaurantDto());

        assertThat(cabinet.restaurant(OWNER_ID, RESTAURANT_ID)).isEqualTo(restaurantDto());
    }

    @Test
    void foreignRestaurantLooksLikeMissingOne() {
        when(restaurantRepository.findByIdAndOwnerId(RESTAURANT_ID, STRANGER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cabinet.restaurant(STRANGER_ID, RESTAURANT_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Restaurant with ID: 1 was not found");
    }

    @Test
    void strangerCannotReadOrChangeAnything() {
        when(restaurantRepository.existsByIdAndOwnerId(RESTAURANT_ID, STRANGER_ID)).thenReturn(false);
        List<ThrowingCallable> attempts = List.of(
                () -> cabinet.changeWorkingHours(STRANGER_ID, RESTAURANT_ID, hours()),
                () -> cabinet.replaceImage(STRANGER_ID, RESTAURANT_ID, IMAGE),
                () -> cabinet.menu(STRANGER_ID, RESTAURANT_ID),
                () -> cabinet.stopList(STRANGER_ID, RESTAURANT_ID),
                () -> cabinet.addMenuItem(STRANGER_ID, RESTAURANT_ID, menuItemRequest()),
                () -> cabinet.updateMenuItem(STRANGER_ID, RESTAURANT_ID, MENU_ITEM_ID, menuItemRequest()),
                () -> cabinet.replaceMenuItemImage(STRANGER_ID, RESTAURANT_ID, MENU_ITEM_ID, IMAGE),
                () -> cabinet.removeMenuItem(STRANGER_ID, RESTAURANT_ID, MENU_ITEM_ID),
                () -> cabinet.stopSelling(STRANGER_ID, RESTAURANT_ID, MENU_ITEM_ID),
                () -> cabinet.resumeSelling(STRANGER_ID, RESTAURANT_ID, MENU_ITEM_ID));

        attempts.forEach(attempt -> assertThatThrownBy(attempt)
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Restaurant with ID: 1 was not found"));
        verifyNoInteractions(restaurantService, menuItemService);
    }

    @Test
    void ownerStopsAndResumesSelling() {
        when(restaurantRepository.existsByIdAndOwnerId(RESTAURANT_ID, OWNER_ID)).thenReturn(true);
        when(menuItemService.withdrawFromSale(RESTAURANT_ID, MENU_ITEM_ID)).thenReturn(menuItemDto(false));
        when(menuItemService.putOnSale(RESTAURANT_ID, MENU_ITEM_ID)).thenReturn(menuItemDto(true));

        assertThat(cabinet.stopSelling(OWNER_ID, RESTAURANT_ID, MENU_ITEM_ID).available()).isFalse();
        assertThat(cabinet.resumeSelling(OWNER_ID, RESTAURANT_ID, MENU_ITEM_ID).available()).isTrue();
    }

    @Test
    void ownerChangesWorkingHours() {
        when(restaurantRepository.existsByIdAndOwnerId(RESTAURANT_ID, OWNER_ID)).thenReturn(true);
        when(restaurantService.changeWorkingHours(RESTAURANT_ID, LocalTime.of(9, 0), LocalTime.of(21, 0)))
                .thenReturn(restaurantDto());

        assertThat(cabinet.changeWorkingHours(OWNER_ID, RESTAURANT_ID, hours())).isEqualTo(restaurantDto());
    }

    @Test
    void ownerEditsMenuAndPhotos() {
        when(restaurantRepository.existsByIdAndOwnerId(RESTAURANT_ID, OWNER_ID)).thenReturn(true);
        when(menuItemService.getWholeMenu(RESTAURANT_ID)).thenReturn(List.of(menuItemDto(true)));
        when(menuItemService.getStopList(RESTAURANT_ID)).thenReturn(List.of(menuItemDto(false)));

        assertThat(cabinet.menu(OWNER_ID, RESTAURANT_ID)).containsExactly(menuItemDto(true));
        assertThat(cabinet.stopList(OWNER_ID, RESTAURANT_ID)).containsExactly(menuItemDto(false));
        cabinet.addMenuItem(OWNER_ID, RESTAURANT_ID, menuItemRequest());
        cabinet.updateMenuItem(OWNER_ID, RESTAURANT_ID, MENU_ITEM_ID, menuItemRequest());
        cabinet.replaceMenuItemImage(OWNER_ID, RESTAURANT_ID, MENU_ITEM_ID, IMAGE);
        cabinet.replaceImage(OWNER_ID, RESTAURANT_ID, IMAGE);
        cabinet.removeMenuItem(OWNER_ID, RESTAURANT_ID, MENU_ITEM_ID);

        verify(menuItemService).addMenuItemToRestaurant(RESTAURANT_ID, menuItemRequest());
        verify(menuItemService).updateMenuItem(RESTAURANT_ID, MENU_ITEM_ID, menuItemRequest());
        verify(menuItemService).replaceImage(RESTAURANT_ID, MENU_ITEM_ID, IMAGE);
        verify(restaurantService).replaceImage(RESTAURANT_ID, IMAGE);
        verify(menuItemService).removeMenuItem(RESTAURANT_ID, MENU_ITEM_ID);
    }

    private static WorkingHoursRequest hours() {
        return new WorkingHoursRequest(LocalTime.of(9, 0), LocalTime.of(21, 0));
    }

    private static MenuItemRequest menuItemRequest() {
        return new MenuItemRequest("Soup", null, new BigDecimal("250.00"), null, true);
    }

    private static RestaurantDto restaurantDto() {
        return new RestaurantDto(RESTAURANT_ID, "Pizzeria", null, null, null, null, null,
                LocalTime.of(9, 0), LocalTime.of(21, 0), true, null, null, null, 0);
    }

    private static MenuItemDto menuItemDto(boolean available) {
        return new MenuItemDto(MENU_ITEM_ID, "Soup", null, new BigDecimal("250.00"), null, available, null,
                MenuSection.DISH, null, RESTAURANT_ID);
    }
}
