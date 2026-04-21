package com.deliveryHub.user.repository;

import com.deliveryHub.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findUserByEmail(String email);
    boolean existsByEmail(String email);
}
