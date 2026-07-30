package com.deliveryhub.user.service;

import com.deliveryhub.user.UserFixtures;
import com.deliveryhub.user.dto.UserDto;
import com.deliveryhub.user.entity.Role;
import com.deliveryhub.user.entity.User;
import com.deliveryhub.user.exception.OwnRoleChangeException;
import com.deliveryhub.user.exception.UserNotFoundException;
import com.deliveryhub.user.mapper.UserMapper;
import com.deliveryhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRolesTest {

    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");
    private static final long ADMIN_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RefreshTokens refreshTokens;

    private UserRoles userRoles;

    @BeforeEach
    void setUp() {
        userRoles = new UserRoles(userRepository, userMapper, refreshTokens, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void makesUserRestaurantOwner() {
        User user = UserFixtures.persistedUser(7L, "owner@example.com", Role.USER);
        UserDto dto = new UserDto(7L, "owner@example.com", "Test", null, "RESTAURANT_OWNER", NOW);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(dto);

        UserDto changed = userRoles.changeRole(7L, Role.RESTAURANT_OWNER, ADMIN_ID);

        assertThat(changed).isEqualTo(dto);
        assertThat(user.getRole()).isEqualTo(Role.RESTAURANT_OWNER);
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
        verify(refreshTokens).revokeAllOf(7L);
    }

    @Test
    void keepsSessionsWhenRoleStaysTheSame() {
        User user = UserFixtures.persistedUser(7L, "owner@example.com", Role.COURIER);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        userRoles.changeRole(7L, Role.COURIER, ADMIN_ID);

        verify(refreshTokens, never()).revokeAllOf(7L);
    }

    @Test
    void rejectsChangeOfOwnRole() {
        assertThatThrownBy(() -> userRoles.changeRole(ADMIN_ID, Role.USER, ADMIN_ID))
                .isInstanceOf(OwnRoleChangeException.class);

        verify(userRepository, never()).findById(ADMIN_ID);
    }

    @Test
    void failsForUnknownUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userRoles.changeRole(404L, Role.COURIER, ADMIN_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void findsUserByEmailIgnoringCaseAndSpaces() {
        User user = UserFixtures.persistedUser(7L, "owner@example.com", Role.USER);
        UserDto dto = new UserDto(7L, "owner@example.com", "Test", null, "USER", NOW);
        when(userRepository.findUserByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(dto);

        assertThat(userRoles.findByEmail("  Owner@Example.com ")).isEqualTo(dto);
    }

    @Test
    void failsForUnknownEmail() {
        when(userRepository.findUserByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userRoles.findByEmail("nobody@example.com"))
                .isInstanceOf(UserNotFoundException.class);
    }
}
