package com.deliveryhub.delivery.quote;

import com.deliveryhub.delivery.dispatch.DeliveryZone;
import com.deliveryhub.delivery.dispatch.DeliveryZones;
import com.deliveryhub.delivery.dispatch.DispatchProperties;
import com.deliveryhub.delivery.entity.GeoPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryQuotesTest {

    private static final GeoPosition RESTAURANT = new GeoPosition(56.4846, 84.9476);
    private static final GeoPosition CUSTOMER = new GeoPosition(56.4700, 84.9700);

    private final DeliveryZones zones = mock(DeliveryZones.class);
    private final StraightLine straightLine = mock(StraightLine.class);
    private final RoutePlanner routePlanner = mock(RoutePlanner.class);
    private final WeatherReport weatherReport = mock(WeatherReport.class);
    private DeliveryQuotes quotes;

    @BeforeEach
    void setUp() {
        PricingProperties pricing = new PricingProperties(new BigDecimal("99"), new BigDecimal("15"), Map.of(
                WeatherCondition.SNOW, new PricingProperties.WeatherRule(new BigDecimal("49"), 25)));
        quotes = new DeliveryQuotes(zones, straightLine, routePlanner, weatherReport, pricing, new DispatchProperties(5000));
        when(zones.find(7L)).thenReturn(Optional.empty());
        when(straightLine.meters(any(), any())).thenReturn(2100.0);
        when(routePlanner.route(RESTAURANT, CUSTOMER)).thenReturn(new Route(2793, 261, Route.RouteSource.OSRM));
    }

    @Test
    void chargesBaseFeePlusStartedTenthsOfKilometer() {
        when(weatherReport.current()).thenReturn(new Weather(WeatherCondition.CLEAR, -5.0));

        QuoteDto quote = quotes.quote(new QuoteRequest(7L, RESTAURANT, CUSTOMER));

        assertThat(quote.deliveryFee()).isEqualByComparingTo("141.00");
        assertThat(quote.etaMinutes()).isEqualTo(5);
        assertThat(quote.distanceMeters()).isEqualTo(2793);
        assertThat(quote.weather().surcharge()).isEqualByComparingTo("0");
        assertThat(quote.weather().extraMinutes()).isZero();
    }

    @Test
    void snowAddsSurchargeAndSlowsCourierDown() {
        when(weatherReport.current()).thenReturn(new Weather(WeatherCondition.SNOW, -8.0));

        QuoteDto quote = quotes.quote(new QuoteRequest(7L, RESTAURANT, CUSTOMER));

        assertThat(quote.deliveryFee()).isEqualByComparingTo("190.00");
        assertThat(quote.weather().condition()).isEqualTo(WeatherCondition.SNOW);
        assertThat(quote.weather().surcharge()).isEqualByComparingTo("49");
        assertThat(quote.weather().extraMinutes()).isEqualTo(2);
        assertThat(quote.etaMinutes()).isEqualTo(7);
    }

    @Test
    void unknownWeatherQuotesWithoutSurcharge() {
        when(weatherReport.current()).thenReturn(Weather.unknown());

        QuoteDto quote = quotes.quote(new QuoteRequest(7L, RESTAURANT, CUSTOMER));

        assertThat(quote.weather().condition()).isEqualTo(WeatherCondition.UNKNOWN);
        assertThat(quote.deliveryFee()).isEqualByComparingTo("141.00");
    }

    @Test
    void rejectsCustomerOutsideStoredZone() {
        when(zones.find(7L)).thenReturn(Optional.of(new DeliveryZone(RESTAURANT, 2000)));

        assertThatThrownBy(() -> quotes.quote(new QuoteRequest(7L, RESTAURANT, CUSTOMER)))
                .isInstanceOf(OutOfDeliveryZoneException.class);
        verify(routePlanner, never()).route(any(), any());
    }

    @Test
    void rejectsRouteMuchLongerThanZoneRadius() {
        when(routePlanner.route(RESTAURANT, CUSTOMER)).thenReturn(new Route(15_001, 1800, Route.RouteSource.OSRM));

        assertThatThrownBy(() -> quotes.quote(new QuoteRequest(7L, RESTAURANT, CUSTOMER)))
                .isInstanceOf(OutOfDeliveryZoneException.class);
    }

    @Test
    void usesDefaultRadiusAroundRestaurantWhenZoneIsNotStoredYet() {
        when(straightLine.meters(RESTAURANT, CUSTOMER)).thenReturn(5001.0);

        assertThatThrownBy(() -> quotes.quote(new QuoteRequest(7L, RESTAURANT, CUSTOMER)))
                .isInstanceOf(OutOfDeliveryZoneException.class);
    }
}
