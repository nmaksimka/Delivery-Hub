package com.deliveryhub.user.service;

import com.deliveryhub.user.entity.Role;
import com.deliveryhub.user.entity.User;
import com.deliveryhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void createsAdminWithEncodedPasswordWhenAbsent() {
        when(userRepository.existsByEmail("admin@deliveryhub.local")).thenReturn(false);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

        bootstrap(" Admin@DeliveryHub.local ", "s3cret-pass").createAdminIfAbsent();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@deliveryhub.local");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded");
    }

    @Test
    void doesNothingWhenAdminAlreadyExists() {
        when(userRepository.existsByEmail("admin@deliveryhub.local")).thenReturn(true);

        bootstrap("admin@deliveryhub.local", "s3cret-pass").createAdminIfAbsent();

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void doesNothingWhenPasswordIsNotConfigured() {
        bootstrap("admin@deliveryhub.local", "").createAdminIfAbsent();

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void doesNothingWhenEmailIsNotConfigured() {
        bootstrap(null, "s3cret-pass").createAdminIfAbsent();

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(new BootstrapProperties(email, password), userRepository, passwordEncoder, clock);
    }
}
