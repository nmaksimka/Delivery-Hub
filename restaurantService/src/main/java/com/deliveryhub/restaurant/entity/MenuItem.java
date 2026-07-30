package com.deliveryhub.restaurant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    private String category;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "image_key")
    private String imageKey;

    @Column(name = "external_source", length = 32)
    private String externalSource;

    @Column(name = "external_id", length = 64)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MenuSection section = MenuSection.DISH;

    @Embedded
    private Nutrition nutrition;

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

    public void changeImage(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void storeImage(String imageKey) {
        this.imageKey = imageKey;
    }

    public void linkToSource(String externalSource, String externalId) {
        this.externalSource = Objects.requireNonNull(externalSource, "externalSource");
        this.externalId = Objects.requireNonNull(externalId, "externalId");
    }

    public void moveToSection(MenuSection section) {
        this.section = Objects.requireNonNull(section, "section");
    }

    public void changeNutrition(Nutrition nutrition) {
        this.nutrition = nutrition == null || nutrition.isEmpty() ? null : nutrition;
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

    public String getImageUrl() {
        return imageUrl;
    }

    public String getImageKey() {
        return imageKey;
    }

    public String getExternalSource() {
        return externalSource;
    }

    public String getExternalId() {
        return externalId;
    }

    public MenuSection getSection() {
        return section;
    }

    public Nutrition getNutrition() {
        return nutrition;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }
}
