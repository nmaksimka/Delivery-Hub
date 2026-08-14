package com.deliveryhub.delivery.quote;

import com.deliveryhub.delivery.entity.GeoPosition;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class StraightLine {

    private static final String DISTANCE = """
            SELECT ST_Distance(ST_SetSRID(ST_MakePoint(:fromLongitude, :fromLatitude), 4326)::geography,
                               ST_SetSRID(ST_MakePoint(:toLongitude, :toLatitude), 4326)::geography)
            """;

    private final JdbcClient jdbcClient;

    public StraightLine(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public double meters(GeoPosition from, GeoPosition to) {
        return jdbcClient.sql(DISTANCE)
                .param("fromLatitude", from.latitude())
                .param("fromLongitude", from.longitude())
                .param("toLatitude", to.latitude())
                .param("toLongitude", to.longitude())
                .query(Double.class)
                .single();
    }
}
