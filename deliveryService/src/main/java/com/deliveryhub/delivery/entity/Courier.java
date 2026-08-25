package com.deliveryhub.delivery.entity;

import com.deliveryhub.delivery.exception.CourierBusyException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "couriers")
@DynamicUpdate
public class Courier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true)
    private Long userId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CourierStatus status;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(columnDefinition = "geography(Point,4326)")
    private Point position;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "rating_sum", nullable = false)
    private long ratingSum;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Version
    @Column(nullable = false)
    private long version;

    protected Courier() {
    }

    private Courier(Long userId, String name, String phone, Instant at) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Courier name must not be blank");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Courier phone must not be blank");
        }
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.status = CourierStatus.OFFLINE;
        this.updatedAt = Objects.requireNonNull(at, "at");
    }

    public static Courier register(Long userId, String name, String phone, Instant at) {
        return new Courier(userId, name, phone, at);
    }

    public void goOnline(Instant at) {
        changeOwnStatus(CourierStatus.AVAILABLE, at);
    }

    public void goOffline(Instant at) {
        changeOwnStatus(CourierStatus.OFFLINE, at);
    }

    public void occupy(Instant at) {
        status = CourierStatus.BUSY;
        updatedAt = Objects.requireNonNull(at, "at");
    }

    public boolean release(Instant at) {
        if (status != CourierStatus.BUSY) {
            return false;
        }
        status = CourierStatus.AVAILABLE;
        updatedAt = Objects.requireNonNull(at, "at");
        return true;
    }

    private void changeOwnStatus(CourierStatus next, Instant at) {
        if (status == CourierStatus.BUSY) {
            throw new CourierBusyException(id);
        }
        status = next;
        updatedAt = Objects.requireNonNull(at, "at");
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public CourierStatus getStatus() {
        return status;
    }

    public GeoPosition getPosition() {
        return GeoPosition.of(position);
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public Double getRating() {
        if (ratingCount == 0) {
            return null;
        }
        return BigDecimal.valueOf(ratingSum)
                .divide(BigDecimal.valueOf(ratingCount), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
