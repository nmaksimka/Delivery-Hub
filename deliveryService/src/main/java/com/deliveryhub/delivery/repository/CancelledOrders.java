package com.deliveryhub.delivery.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class CancelledOrders {

    private static final String REMEMBER = """
            INSERT INTO cancelled_orders (order_id, cancelled_at)
            VALUES (:orderId, :cancelledAt)
            ON CONFLICT (order_id) DO NOTHING
            """;

    private final JdbcClient jdbcClient;

    public CancelledOrders(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void remember(long orderId, Instant cancelledAt) {
        jdbcClient.sql(REMEMBER)
                .param("orderId", orderId)
                .param("cancelledAt", Timestamp.from(cancelledAt))
                .update();
    }

    public boolean contains(long orderId) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM cancelled_orders WHERE order_id = :orderId)")
                .param("orderId", orderId)
                .query(Boolean.class)
                .single();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int forgetCancelledBefore(Instant before) {
        return jdbcClient.sql("DELETE FROM cancelled_orders WHERE cancelled_at < :before")
                .param("before", Timestamp.from(before))
                .update();
    }
}
