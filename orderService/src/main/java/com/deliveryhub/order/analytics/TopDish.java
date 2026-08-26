package com.deliveryhub.order.analytics;

import java.math.BigDecimal;

public record TopDish(int rank, Long menuItemId, String name, long quantity, BigDecimal revenue) {
}
