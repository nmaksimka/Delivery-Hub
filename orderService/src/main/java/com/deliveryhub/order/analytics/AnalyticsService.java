package com.deliveryhub.order.analytics;

import com.deliveryhub.order.client.RestaurantOwnership;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@EnableConfigurationProperties(AnalyticsProperties.class)
public class AnalyticsService {

    private final AnalyticsRepository repository;
    private final RestaurantOwnership ownership;
    private final AnalyticsProperties properties;
    private final Clock clock;

    public AnalyticsService(AnalyticsRepository repository, RestaurantOwnership ownership,
                            AnalyticsProperties properties, Clock clock) {
        this.repository = repository;
        this.ownership = ownership;
        this.properties = properties;
        this.clock = clock;
    }

    public AnalyticsReport forAdmin(LocalDate from, LocalDate to, Long restaurantId) {
        return report(from, to, restaurantId);
    }

    public AnalyticsReport forOwner(Long ownerId, Long restaurantId, LocalDate from, LocalDate to) {
        ownership.requireOwner(ownerId, restaurantId);
        return report(from, to, restaurantId);
    }

    private AnalyticsReport report(LocalDate requestedFrom, LocalDate requestedTo, Long restaurantId) {
        LocalDate to = requestedTo != null ? requestedTo : LocalDate.now(clock.withZone(properties.zone()));
        LocalDate from = requestedFrom != null ? requestedFrom : to.minusDays(properties.defaultPeriodDays() - 1L);
        if (from.isAfter(to)) {
            throw new InvalidPeriodException("Period start " + from + " is after its end " + to);
        }
        if (ChronoUnit.DAYS.between(from, to) >= properties.maxPeriodDays()) {
            throw new InvalidPeriodException("Period is longer than " + properties.maxPeriodDays() + " days");
        }
        Instant start = from.atStartOfDay(properties.zone()).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(properties.zone()).toInstant();
        String zone = properties.zone().getId();
        return new AnalyticsReport(from, to, restaurantId,
                repository.revenueByDay(start, end, zone, restaurantId),
                repository.topDishes(start, end, zone, restaurantId, properties.topDishesLimit()),
                repository.deliveryTimeOverall(start, end, zone, restaurantId).orElse(null),
                repository.deliveryTimeByDay(start, end, zone, restaurantId));
    }
}
