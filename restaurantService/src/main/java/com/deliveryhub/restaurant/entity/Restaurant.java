package com.deliveryhub.restaurant.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "restaurants")
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String address;

    private String phone;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    private String cuisine;

    private String city;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "image_key")
    private String imageKey;

    @Column(name = "external_source", length = 32)
    private String externalSource;

    @Column(name = "external_id", length = 64)
    private String externalId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "owner_id")
    private Long ownerId;

    @OneToMany(mappedBy = "restaurant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MenuItem> menuItems = new ArrayList<>();

    protected Restaurant() {
    }

    public Restaurant(String name, String address, String phone) {
        changeContacts(name, address, phone);
        this.active = true;
    }

    public void changeContacts(String name, String address, String phone) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Restaurant name must not be blank");
        }
        this.name = name;
        this.address = address;
        this.phone = phone;
    }

    public static Restaurant imported(String externalSource, String externalId, String name, String address,
                                      String phone) {
        Restaurant restaurant = new Restaurant(name, address, phone);
        restaurant.externalSource = Objects.requireNonNull(externalSource, "externalSource");
        restaurant.externalId = Objects.requireNonNull(externalId, "externalId");
        return restaurant;
    }

    public void describe(String cuisine, String city, String imageUrl) {
        this.cuisine = cuisine;
        this.city = city;
        this.imageUrl = imageUrl;
    }

    public void useImportedImage(String imageUrl) {
        if (this.imageUrl == null) {
            this.imageUrl = imageUrl;
        }
    }

    public void storeImage(String imageKey) {
        this.imageKey = imageKey;
    }

    public void changeWorkingHours(LocalTime openTime, LocalTime closeTime) {
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public boolean isOpenAt(LocalTime time) {
        if (openTime == null || closeTime == null || openTime.equals(closeTime)) {
            return true;
        }
        if (openTime.isBefore(closeTime)) {
            return !time.isBefore(openTime) && time.isBefore(closeTime);
        }
        return !time.isBefore(openTime) || time.isBefore(closeTime);
    }

    public void assignOwner(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw new IllegalArgumentException("Owner id must be positive");
        }
        this.ownerId = ownerId;
    }

    public void removeOwner() {
        ownerId = null;
    }

    public boolean hasOwner() {
        return ownerId != null;
    }

    public boolean isOwnedBy(Long userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public MenuItem addMenuItem(String name, String description, BigDecimal price, String category) {
        MenuItem item = new MenuItem(this, name, description, price, category);
        menuItems.add(item);
        return item;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getCuisine() {
        return cuisine;
    }

    public String getCity() {
        return city;
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

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public boolean isActive() {
        return active;
    }

    public List<MenuItem> getMenuItems() {
        return Collections.unmodifiableList(menuItems);
    }
}
