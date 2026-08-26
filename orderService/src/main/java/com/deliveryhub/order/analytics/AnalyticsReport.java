package com.deliveryhub.order.analytics;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsReport(
        LocalDate from,
        LocalDate to,
        Long restaurantId,
        List<DailyRevenue> revenueByDay,
        List<TopDish> topDishes,
        DeliveryTimeStats deliveryTime,
        List<DeliveryTimeStats> deliveryTimeByDay) {
}
