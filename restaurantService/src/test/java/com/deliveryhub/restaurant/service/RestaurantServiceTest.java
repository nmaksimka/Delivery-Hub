package com.deliveryhub.restaurant.service;

import com.deliveryhub.restaurant.dto.RestaurantDto;
import com.deliveryhub.restaurant.entity.Restaurant;
import com.deliveryhub.restaurant.mapper.RestaurantMapper;
import com.deliveryhub.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(restaurantMapper.toDto(any())).thenReturn(new RestaurantDto());

        assertThat(restaurantService.getAllRestaurants(true)).hasSize(1);
        verify(restaurantRepository, never()).findAll();
    }

    @Test
    @DisplayName("activeOnly=false отдаёт все рестораны")
    void returnsAllWhenRequested() {
        when(restaurantRepository.findAll()).thenReturn(List.of(restaurant(), restaurant()));
        when(restaurantMapper.toDto(any())).thenReturn(new RestaurantDto());

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
    @DisplayName("обновление не подменяет id существующего ресторана")
    void updateKeepsEntityIdentity() {
        Restaurant existing = restaurant();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(restaurantMapper.toDto(existing)).thenReturn(new RestaurantDto());

        RestaurantDto incoming = RestaurantDto.builder().id(999L).name("Hacked").build();
        restaurantService.updateRestaurant(1L, incoming);

        verify(restaurantMapper).updateEntityFromDto(incoming, existing);
        assertThat(existing.getId()).isEqualTo(1L);
    }

    private static Restaurant restaurant() {
        return Restaurant.builder().id(1L).name("Pizzeria").active(true).build();
    }
}
