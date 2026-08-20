package com.deliveryhub.delivery.quote;

import com.deliveryhub.delivery.dispatch.DeliveryZone;
import com.deliveryhub.delivery.dispatch.DeliveryZones;
import com.deliveryhub.delivery.dispatch.DispatchProperties;
import com.deliveryhub.delivery.entity.GeoPosition;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DeliveryQuotes {

    private static final BigDecimal METERS_IN_KM = BigDecimal.valueOf(1000);
    private static final int MAX_ROUTE_TO_ZONE_RATIO = 3;

    private final DeliveryZones zones;
    private final StraightLine straightLine;
    private final RoutePlanner routePlanner;
    private final WeatherReport weatherReport;
    private final PricingProperties pricing;
    private final DispatchProperties dispatch;

    public DeliveryQuotes(DeliveryZones zones, StraightLine straightLine, RoutePlanner routePlanner,
                          WeatherReport weatherReport, PricingProperties pricing, DispatchProperties dispatch) {
        this.zones = zones;
        this.straightLine = straightLine;
        this.routePlanner = routePlanner;
        this.weatherReport = weatherReport;
        this.pricing = pricing;
        this.dispatch = dispatch;
    }

    public QuoteDto quote(QuoteRequest request) {
        DeliveryZone zone = requireInsideZone(request.restaurantId(), request.from(), request.to());
        Route route = routePlanner.route(request.from(), request.to());
        int maxRouteMeters = zone.maxDistanceMeters() * MAX_ROUTE_TO_ZONE_RATIO;
        if (route.distanceMeters() > maxRouteMeters) {
            throw new OutOfDeliveryZoneException(request.restaurantId(), route.distanceMeters(), maxRouteMeters);
        }
        Weather weather = weatherReport.current();
        PricingProperties.WeatherRule rule = pricing.ruleFor(weather.condition());

        int routeMinutes = Math.max(1, (int) Math.ceil(route.durationSeconds() / 60.0));
        int extraMinutes = (int) Math.ceil(routeMinutes * rule.etaPercent() / 100.0);
        BigDecimal fee = tariff(route.distanceMeters()).add(rule.surcharge()).setScale(2, RoundingMode.HALF_UP);

        return new QuoteDto(route.distanceMeters(), routeMinutes + extraMinutes, fee, route.source(),
                new QuoteDto.WeatherDto(weather.condition(), weather.temperatureCelsius(),
                        rule.surcharge().setScale(2, RoundingMode.HALF_UP), extraMinutes));
    }

    private BigDecimal tariff(int distanceMeters) {
        BigDecimal kilometers = BigDecimal.valueOf(distanceMeters).divide(METERS_IN_KM, 1, RoundingMode.UP);
        return pricing.baseFee().add(pricing.perKm().multiply(kilometers)).setScale(0, RoundingMode.HALF_UP);
    }

    private DeliveryZone requireInsideZone(long restaurantId, GeoPosition restaurant, GeoPosition customer) {
        DeliveryZone zone = zones.find(restaurantId)
                .orElseGet(() -> new DeliveryZone(restaurant, dispatch.zoneRadiusMeters()));
        int distance = (int) Math.round(straightLine.meters(zone.center(), customer));
        if (distance > zone.maxDistanceMeters()) {
            throw new OutOfDeliveryZoneException(restaurantId, distance, zone.maxDistanceMeters());
        }
        return zone;
    }
}
