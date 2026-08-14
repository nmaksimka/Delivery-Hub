package com.deliveryhub.delivery.dispatch;

import com.deliveryhub.delivery.entity.GeoPosition;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Component
public class DeliveryZones {

    private static final String REMEMBER = """
            INSERT INTO delivery_zones (restaurant_id, center, area, max_distance_m, updated_at)
            VALUES (:restaurantId,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                    ST_Buffer(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radius),
                    :radius,
                    :at)
            ON CONFLICT (restaurant_id) DO UPDATE
            SET center     = EXCLUDED.center,
                area       = ST_Buffer(EXCLUDED.center, delivery_zones.max_distance_m),
                updated_at = EXCLUDED.updated_at
            WHERE NOT ST_Equals(delivery_zones.center::geometry, EXCLUDED.center::geometry)
            """;

    private static final String FIND = """
            SELECT ST_Y(center::geometry) AS latitude, ST_X(center::geometry) AS longitude, max_distance_m
            FROM delivery_zones
            WHERE restaurant_id = :restaurantId
            """;

    private final JdbcClient jdbcClient;

    public DeliveryZones(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DeliveryZone remember(long restaurantId, GeoPosition center, int defaultRadiusMeters, Instant at) {
        jdbcClient.sql(REMEMBER)
                .param("restaurantId", restaurantId)
                .param("latitude", center.latitude())
                .param("longitude", center.longitude())
                .param("radius", defaultRadiusMeters)
                .param("at", Timestamp.from(at))
                .update();
        return find(restaurantId).orElseThrow();
    }

    public Optional<DeliveryZone> find(long restaurantId) {
        return jdbcClient.sql(FIND)
                .param("restaurantId", restaurantId)
                .query((rs, row) -> new DeliveryZone(
                        new GeoPosition(rs.getDouble("latitude"), rs.getDouble("longitude")),
                        rs.getInt("max_distance_m")))
                .optional();
    }
}
