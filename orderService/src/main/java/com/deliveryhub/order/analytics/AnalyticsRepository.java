package com.deliveryhub.order.analytics;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class AnalyticsRepository {

    private static final String DELIVERED_IN_PERIOD = """
            o.status = 'DELIVERED'
              AND o.created_at >= :from AND o.created_at < :to
              AND (CAST(:restaurantId AS BIGINT) IS NULL OR o.restaurant_id = :restaurantId)
            """;

    private static final String REVENUE_BY_DAY = """
            WITH daily AS (
                SELECT (o.created_at AT TIME ZONE :zone)::date AS day,
                       COUNT(*) AS orders,
                       SUM(o.total_amount) AS revenue
                FROM orders o
                WHERE %s
                GROUP BY 1
            )
            SELECT day,
                   orders,
                   revenue,
                   SUM(revenue) OVER (ORDER BY day) AS cumulative,
                   ROUND(AVG(revenue) OVER (ORDER BY day ROWS BETWEEN 6 PRECEDING AND CURRENT ROW), 2) AS moving_average,
                   revenue - LAG(revenue) OVER (ORDER BY day) AS change
            FROM daily
            ORDER BY day
            """.formatted(DELIVERED_IN_PERIOD);

    private static final String TOP_DISHES = """
            SELECT RANK() OVER (ORDER BY SUM(oi.quantity) DESC, SUM(oi.quantity * oi.price_per_unit) DESC) AS rank,
                   oi.menu_item_id,
                   MAX(oi.item_name) AS name,
                   SUM(oi.quantity) AS quantity,
                   SUM(oi.quantity * oi.price_per_unit) AS revenue
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE %s
            GROUP BY oi.menu_item_id
            ORDER BY rank, oi.menu_item_id
            LIMIT :limit
            """.formatted(DELIVERED_IN_PERIOD);

    private static final String DELIVERY_TIME_BY_DAY = """
            SELECT (o.created_at AT TIME ZONE :zone)::date AS day,
                   COUNT(*) AS orders,
                   ROUND(AVG(minutes.value), 1) AS average_minutes,
                   ROUND((percentile_cont(0.5) WITHIN GROUP (ORDER BY minutes.value))::numeric, 1) AS median_minutes,
                   ROUND((percentile_cont(0.9) WITHIN GROUP (ORDER BY minutes.value))::numeric, 1) AS p90_minutes
            FROM orders o
            CROSS JOIN LATERAL (SELECT EXTRACT(EPOCH FROM o.updated_at - o.created_at) / 60 AS value) minutes
            WHERE %s
            GROUP BY 1
            ORDER BY 1
            """.formatted(DELIVERED_IN_PERIOD);

    private static final String DELIVERY_TIME_OVERALL = """
            SELECT CAST(NULL AS DATE) AS day,
                   COUNT(*) AS orders,
                   ROUND(AVG(minutes.value), 1) AS average_minutes,
                   ROUND((percentile_cont(0.5) WITHIN GROUP (ORDER BY minutes.value))::numeric, 1) AS median_minutes,
                   ROUND((percentile_cont(0.9) WITHIN GROUP (ORDER BY minutes.value))::numeric, 1) AS p90_minutes
            FROM orders o
            CROSS JOIN LATERAL (SELECT EXTRACT(EPOCH FROM o.updated_at - o.created_at) / 60 AS value) minutes
            WHERE %s
            """.formatted(DELIVERED_IN_PERIOD);

    private final JdbcClient jdbcClient;

    public AnalyticsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<DailyRevenue> revenueByDay(Instant from, Instant to, String zone, Long restaurantId) {
        return filtered(REVENUE_BY_DAY, from, to, zone, restaurantId)
                .query((rs, row) -> new DailyRevenue(rs.getObject("day", LocalDate.class), rs.getLong("orders"),
                        rs.getBigDecimal("revenue"), rs.getBigDecimal("cumulative"),
                        rs.getBigDecimal("moving_average"), rs.getBigDecimal("change")))
                .list();
    }

    public List<TopDish> topDishes(Instant from, Instant to, String zone, Long restaurantId, int limit) {
        return filtered(TOP_DISHES, from, to, zone, restaurantId)
                .param("limit", limit)
                .query((rs, row) -> new TopDish(rs.getInt("rank"), rs.getLong("menu_item_id"), rs.getString("name"),
                        rs.getLong("quantity"), rs.getBigDecimal("revenue")))
                .list();
    }

    public List<DeliveryTimeStats> deliveryTimeByDay(Instant from, Instant to, String zone, Long restaurantId) {
        return filtered(DELIVERY_TIME_BY_DAY, from, to, zone, restaurantId)
                .query((rs, row) -> deliveryTime(rs))
                .list();
    }

    public Optional<DeliveryTimeStats> deliveryTimeOverall(Instant from, Instant to, String zone, Long restaurantId) {
        return filtered(DELIVERY_TIME_OVERALL, from, to, zone, restaurantId)
                .query((rs, row) -> deliveryTime(rs))
                .optional()
                .filter(stats -> stats.orders() > 0);
    }

    private JdbcClient.StatementSpec filtered(String sql, Instant from, Instant to, String zone, Long restaurantId) {
        return jdbcClient.sql(sql)
                .param("from", from.atOffset(ZoneOffset.UTC))
                .param("to", to.atOffset(ZoneOffset.UTC))
                .param("zone", zone)
                .param("restaurantId", restaurantId, Types.BIGINT);
    }

    private static DeliveryTimeStats deliveryTime(ResultSet rs) throws SQLException {
        return new DeliveryTimeStats(rs.getObject("day", LocalDate.class), rs.getLong("orders"),
                rs.getBigDecimal("average_minutes"), rs.getBigDecimal("median_minutes"),
                rs.getBigDecimal("p90_minutes"));
    }
}
