package com.deliveryhub.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(name = "phone", length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    private User(String email, String passwordHash, String name, String phone, Role role, Instant registeredAt) {
        this.email = Objects.requireNonNull(email, "email");
        this.password = Objects.requireNonNull(passwordHash, "passwordHash");
        this.name = Objects.requireNonNull(name, "name");
        this.phone = phone;
        this.role = Objects.requireNonNull(role, "role");
        this.createdAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.updatedAt = registeredAt;
    }

    public static User register(String email, String passwordHash, String name, String phone,
                                Instant registeredAt) {
        return new User(normalizeEmail(email), passwordHash, name, phone, Role.USER, registeredAt);
    }

    public static User admin(String email, String passwordHash, String name, Instant createdAt) {
        return new User(normalizeEmail(email), passwordHash, name, null, Role.ADMIN, createdAt);
    }

    public void changeRole(Role role, Instant changedAt) {
        this.role = Objects.requireNonNull(role, "role");
        this.updatedAt = Objects.requireNonNull(changedAt, "changedAt");
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email").strip().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getPassword() {
        return password;
    }
}
