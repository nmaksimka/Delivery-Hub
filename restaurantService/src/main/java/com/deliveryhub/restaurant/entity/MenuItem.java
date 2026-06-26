package com.deliveryhub.restaurant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    private String category;

    @Column(nullable = false)
    private boolean available;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    protected MenuItem() {
    }

    MenuItem(Restaurant restaurant, String name, String description, BigDecimal price, String category) {
        this.restaurant = Objects.requireNonNull(restaurant, "restaurant");
        this.available = true;
        changeDetails(name, description, price, category);
    }

    public void changeDetails(String name, String description, BigDecimal price, String category) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Menu item name must not be blank");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Menu item price must be positive");
        }
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
    }

    public void putOnSale() {
        available = true;
    }

    public void withdrawFromSale() {
        available = false;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    public boolean isAvailable() {
        return available;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }
}
