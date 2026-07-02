package com.deliveryhub.user;

import com.deliveryhub.user.entity.Role;
import com.deliveryhub.user.entity.User;
import com.deliveryhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(PostgresContainerConfig.class)
class UserRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-07-14T09:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesUserWithTimestampsAndDefaultRole() {
        User saved = userRepository.saveAndFlush(User.register("anna@example.com", "hash", "Anna", "+79990000000", NOW));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void findsUserByEmail() {
        userRepository.saveAndFlush(User.register("boris@example.com", "hash", "Boris", null, NOW));

        assertThat(userRepository.findUserByEmail("boris@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("boris@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void databaseRejectsDuplicateEmail() {
        userRepository.saveAndFlush(User.register("dup@example.com", "hash", "First", null, NOW));

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.register("dup@example.com", "hash", "Second", null, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
