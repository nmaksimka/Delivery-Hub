package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.dto.RestaurantRequest;
import com.deliveryhub.restaurant.dto.RestaurantView;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mapper.RestaurantMapper;
import com.deliveryhub.restaurant.outbox.CatalogChanges;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import com.deliveryhub.restaurant.storage.ImageIntake;
import com.deliveryhub.restaurant.storage.UnsupportedImageException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantService")
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private RestaurantMapper restaurantMapper;
    @Mock
    private ImageIntake imageIntake;
    @Mock
    private CatalogCache catalogCache;
    @Mock
    private CatalogChanges catalogChanges;

    @InjectMocks
    private RestaurantService restaurantService;

    @Test
    @DisplayName("список ресторанов читается из кэша каталога и собирается заново с подписанными ссылками")
    void readsListFromCatalogCache() {
        RestaurantView view = view();
        when(catalogCache.restaurants(true)).thenReturn(List.of(view));
        when(restaurantMapper.fromView(view)).thenReturn(dto());

        assertThat(restaurantService.getAllRestaurants(true)).containsExactly(dto());
        verify(restaurantRepository, never()).findAll();
        verify(restaurantRepository, never()).findByActiveTrue();
    }

    @Test
    @DisplayName("activeOnly=false запрашивает в кэше полный список")
    void asksCacheForFullListWhenRequested() {
        when(catalogCache.restaurants(false)).thenReturn(List.of(view(), view()));
        when(restaurantMapper.fromView(any())).thenReturn(dto());

        assertThat(restaurantService.getAllRestaurants(false)).hasSize(2);
        verify(catalogCache, never()).restaurants(true);
    }

    @Test
    @DisplayName("ресторан по id читается из кэша каталога")
    void readsRestaurantFromCatalogCache() {
        RestaurantView view = view();
        when(catalogCache.restaurant(1L)).thenReturn(view);
        when(restaurantMapper.fromView(view)).thenReturn(dto());

        assertThat(restaurantService.getRestaurantById(1L)).isEqualTo(dto());
    }

    @Test
    @DisplayName("бросает EntityNotFoundException для несуществующего ресторана")
    void failsWhenMissing() {
        when(catalogCache.restaurant(404L))
                .thenThrow(new EntityNotFoundException("Restaurant with ID: 404 was not found"));

        assertThatThrownBy(() -> restaurantService.getRestaurantById(404L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("не удаляет несуществующий ресторан")
    void doesNotDeleteMissing() {
        when(restaurantRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.removeRestaurant(404L))
                .isInstanceOf(EntityNotFoundException.class);
        verify(restaurantRepository, never()).delete(any());
        verify(catalogCache, never()).evictAll();
    }

    @Test
    @DisplayName("удаление ресторана сбрасывает весь кэш каталога")
    void removalEvictsWholeCatalog() {
        Restaurant existing = restaurant();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(existing));

        restaurantService.removeRestaurant(1L);

        verify(restaurantRepository).delete(existing);
        verify(catalogCache).evictAll();
    }

    @Test
    @DisplayName("удаление ресторана убирает из хранилища его фото и фото блюд")
    void removalDiscardsStoredImages() {
        Restaurant existing = restaurant();
        existing.storeImage("restaurants/1/photo");
        existing.addMenuItem("Soup", null, new BigDecimal("250.00"), "soup").storeImage("menu/1/2/photo");
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(existing));

        restaurantService.removeRestaurant(1L);

        verify(imageIntake).discardAfterCommit("restaurants/1/photo");
        verify(imageIntake).discardAfterCommit("menu/1/2/photo");
    }

    @Test
    @DisplayName("новый ресторан активен, если флаг не передан")
    void createsActiveRestaurantByDefault() {
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        restaurantService.createRestaurant(new RestaurantRequest(
                "Pizzeria", "Lenina 1", null, LocalTime.of(10, 0), LocalTime.of(22, 0), null, 56.4884, 84.948));

        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getOpenTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(captor.getValue().getLatitude()).isEqualTo(56.4884);
        verify(catalogCache).evictRestaurants();
    }

    @Test
    @DisplayName("обновление меняет контакты и может выключить ресторан")
    void updateChangesContactsAndActivity() {
        Restaurant existing = restaurant();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(existing));

        restaurantService.updateRestaurant(1L, new RestaurantRequest(
                "Trattoria", "Mira 5", "+79991112233", null, null, false, null, null));

        assertThat(existing.getName()).isEqualTo("Trattoria");
        assertThat(existing.getAddress()).isEqualTo("Mira 5");
        assertThat(existing.isActive()).isFalse();
        verify(catalogCache).evictRestaurants();
    }

    @Test
    @DisplayName("обновление без координат оставляет точку на карте")
    void updateWithoutCoordinatesKeepsLocation() {
        Restaurant existing = restaurant();
        existing.locate(56.4884, 84.948);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(existing));

        restaurantService.updateRestaurant(1L, new RestaurantRequest(
                "Trattoria", "Mira 5", "+79991112233", null, null, true, null, null));

        assertThat(existing.getLatitude()).isEqualTo(56.4884);
        assertThat(existing.getLongitude()).isEqualTo(84.948);
    }

    @Test
    @DisplayName("замена картинки сохраняет объект под новым ключом и планирует удаление старого")
    void replacesImageAndDiscardsPreviousOne() {
        Restaurant restaurant = restaurant();
        restaurant.storeImage("restaurants/1/old");
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(restaurantMapper.toDto(restaurant)).thenReturn(dto());
        byte[] content = {1, 2, 3};

        restaurantService.replaceImage(1L, content);

        assertThat(restaurant.getImageKey()).startsWith("restaurants/1/").isNotEqualTo("restaurants/1/old");
        var order = inOrder(imageIntake);
        order.verify(imageIntake).save(eq(restaurant.getImageKey()), eq(content));
        order.verify(imageIntake).discardAfterCommit("restaurants/1/old");
        verify(catalogCache).evictRestaurants();
    }

    @Test
    @DisplayName("отклонённая картинка не меняет ключ ресторана")
    void keepsKeyWhenImageIsRejected() {
        Restaurant restaurant = restaurant();
        restaurant.storeImage("restaurants/1/old");
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        doThrow(new UnsupportedImageException()).when(imageIntake).save(anyString(), any());

        assertThatThrownBy(() -> restaurantService.replaceImage(1L, new byte[]{1}))
                .isInstanceOf(UnsupportedImageException.class);

        assertThat(restaurant.getImageKey()).isEqualTo("restaurants/1/old");
        verify(imageIntake, never()).discardAfterCommit(any());
        verify(catalogCache, never()).evictRestaurants();
    }

    @Test
    @DisplayName("замена картинки несуществующего ресторана — EntityNotFoundException")
    void failsToReplaceImageOfMissingRestaurant() {
        when(restaurantRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.replaceImage(404L, new byte[]{1}))
                .isInstanceOf(EntityNotFoundException.class);
        verify(imageIntake, never()).save(any(), any());
    }

    @Test
    void workingHoursChangeEvictsRestaurants() {
        Restaurant restaurant = restaurant();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        restaurantService.changeWorkingHours(1L, LocalTime.of(9, 0), LocalTime.of(21, 0));

        assertThat(restaurant.getOpenTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(restaurant.getCloseTime()).isEqualTo(LocalTime.of(21, 0));
        verify(catalogCache).evictRestaurants();
    }

    @Test
    void assignsAndRemovesOwner() {
        Restaurant restaurant = restaurant();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

        assertThat(restaurantService.assignOwner(1L, 7L).ownerId()).isEqualTo(7L);
        assertThat(restaurantService.getOwner(1L).ownerId()).isEqualTo(7L);

        restaurantService.removeOwner(1L);

        assertThat(restaurant.getOwnerId()).isNull();
    }

    @Test
    void doesNotAssignOwnerToMissingRestaurant() {
        when(restaurantRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.assignOwner(404L, 7L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private static Restaurant restaurant() {
        return new Restaurant("Pizzeria", "Lenina 1", null);
    }

    private static RestaurantView view() {
        return new RestaurantView(1L, "Pizzeria", "Lenina 1", null, null, null, null, "restaurants/1/key", null, null, true, null, null, null, 0);
    }

    private static RestaurantDto dto() {
        return new RestaurantDto(1L, "Pizzeria", "Lenina 1", null, null, null, null, null, null, true, null, null, null, 0);
    }
}
