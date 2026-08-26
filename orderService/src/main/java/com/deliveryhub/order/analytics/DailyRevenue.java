package com.deliveryhub.order.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyRevenue(
        LocalDate day,
        long orders,
        BigDecimal revenue,
        BigDecimal cumulative,
        BigDecimal movingAverage,
        BigDecimal change) {
}
