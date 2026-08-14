package com.deliveryhub.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "addresses")
public class Address {

    public static final String SUPPORTED_CITY = "Томск";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 40)
    private String label;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 160)
    private String street;

    @Column(nullable = false, length = 20)
    private String house;

    @Column(length = 20)
    private String apartment;

    @Column(length = 20)
    private String entrance;

    @Column(length = 20)
    private String floor;

    @Column(name = "courier_comment", length = 300)
    private String courierComment;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "geo_status", nullable = false, length = 20)
    private GeoStatus geoStatus;

    @Column(name = "is_default", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Address() {
    }

    private Address(Long userId, AddressDetails details, boolean primary, Instant now) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.primary = primary;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
        this.geoStatus = GeoStatus.PENDING;
        apply(details);
    }

    public static Address create(Long userId, AddressDetails details, boolean primary, Instant now) {
        return new Address(userId, details, primary, now);
    }

    public void change(AddressDetails details, Instant now) {
        String previousLocation = mapQuery();
        apply(details);
        if (!previousLocation.equals(mapQuery())) {
            forgetLocation();
        }
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void locate(GeoPoint point) {
        Objects.requireNonNull(point, "point");
        this.latitude = point.latitude();
        this.longitude = point.longitude();
        this.geoStatus = GeoStatus.LOCATED;
    }

    public void markNotOnMap() {
        this.latitude = null;
        this.longitude = null;
        this.geoStatus = GeoStatus.NOT_FOUND;
    }

    public String mapQuery() {
        return street + ", " + house;
    }

    public boolean isGeocoded() {
        return geoStatus == GeoStatus.LOCATED;
    }

    private void forgetLocation() {
        this.latitude = null;
        this.longitude = null;
        this.geoStatus = GeoStatus.PENDING;
    }

    public void markPrimary(Instant now) {
        this.primary = true;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    private void apply(AddressDetails details) {
        Objects.requireNonNull(details, "details");
        if (!SUPPORTED_CITY.equalsIgnoreCase(details.city().strip())) {
            throw new IllegalArgumentException("Only city " + SUPPORTED_CITY + " is supported");
        }
        this.label = details.label().strip();
        this.city = SUPPORTED_CITY;
        this.street = details.street().strip();
        this.house = details.house().strip();
        this.apartment = blankToNull(details.apartment());
        this.entrance = blankToNull(details.entrance());
        this.floor = blankToNull(details.floor());
        this.courierComment = blankToNull(details.courierComment());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getLabel() {
        return label;
    }

    public String getCity() {
        return city;
    }

    public String getStreet() {
        return street;
    }

    public String getHouse() {
        return house;
    }

    public String getApartment() {
        return apartment;
    }

    public String getEntrance() {
        return entrance;
    }

    public String getFloor() {
        return floor;
    }

    public String getCourierComment() {
        return courierComment;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public GeoStatus getGeoStatus() {
        return geoStatus;
    }

    public boolean isPrimary() {
        return primary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
