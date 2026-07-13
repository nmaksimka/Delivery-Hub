package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.dto.RestaurantRequest;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mapper.RestaurantMapper;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private RestaurantService restaurantService;

    @Test
    @DisplayName("activeOnly=true отдаёт только активные рестораны")
    void filtersInactiveByDefault() {
        when(restaurantRepository.findByActiveTrue()).thenReturn(List.of(restaurant()));
        when(restaurantMapper.toDto(any())).thenReturn(dto());

        assertThat(restaurantService.getAllRestaurants(true)).hasSize(1);
        verify(restaurantRepository, never()).findAll();
    }

    @Test
    @DisplayName("activeOnly=false отдаёт все рестораны")
    void returnsAllWhenRequested() {
        when(restaurantRepository.findAll()).thenReturn(List.of(restaurant(), restaurant()));
        when(restaurantMapper.toDto(any())).thenReturn(dto());

        assertThat(restaurantService.getAllRestaurants(false)).hasSize(2);
        verify(restaurantRepository, never()).findByActiveTrue();
    }

    @Test
    @DisplayName("бросает EntityNotFoundException для несуществующего ресторана")
    void failsWhenMissing() {
        when(restaurantRepository.findById(404L)).thenReturn(Optional.empty());

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
    }

    @Test
    @DisplayName("новый ресторан активен, если флаг не передан")
    void createsActiveRestaurantByDefault() {
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> inv.getArgument(0));

        restaurantService.createRestaurant(new RestaurantRequest(
                "Pizzeria", "Lenina 1", null, LocalTime.of(10, 0), LocalTime.of(22, 0), null));

        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getOpenTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("обновление меняет контакты и может выключить ресторан")
    void updateChangesContactsAndActivity() {
        Restaurant existing = restaurant();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(existing));

        restaurantService.updateRestaurant(1L, new RestaurantRequest(
                "Trattoria", "Mira 5", "+79991112233", null, null, false));

        assertThat(existing.getName()).isEqualTo("Trattoria");
        assertThat(existing.getAddress()).isEqualTo("Mira 5");
        assertThat(existing.isActive()).isFalse();
    }

    private static Restaurant restaurant() {
        return new Restaurant("Pizzeria", "Lenina 1", null);
    }

    private static RestaurantDto dto() {
        return new RestaurantDto(1L, "Pizzeria", "Lenina 1", null, null, null, null, null, null, true);
    }
}
