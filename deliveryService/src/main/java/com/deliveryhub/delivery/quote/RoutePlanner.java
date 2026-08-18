package com.deliveryhub.delivery.quote;

import com.deliveryhub.delivery.entity.GeoPosition;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Locale;

public class RoutePlanner {

    private static final Logger log = LoggerFactory.getLogger(RoutePlanner.class);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final StraightLine straightLine;
    private final RoutingProperties properties;

    public RoutePlanner(RestClient restClient, CircuitBreaker circuitBreaker, StraightLine straightLine,
                        RoutingProperties properties) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
        this.straightLine = straightLine;
        this.properties = properties;
    }

    public Route route(GeoPosition from, GeoPosition to) {
        try {
            OsrmResponse response = circuitBreaker.executeSupplier(() -> restClient.get()
                    .uri("/route/v1/" + properties.profile() + "/" + coordinate(from) + ";" + coordinate(to)
                            + "?overview=false")
                    .retrieve()
                    .body(OsrmResponse.class));
            if (response != null && "Ok".equals(response.code()) && response.routes() != null
                    && !response.routes().isEmpty()) {
                OsrmRoute best = response.routes().getFirst();
                return new Route((int) Math.round(best.distance()), (int) Math.round(best.duration()), Route.RouteSource.OSRM);
            }
            log.info("OSRM found no route, falling back to straight line: {}", response == null ? null : response.code());
        } catch (RestClientException | CallNotPermittedException e) {
            log.warn("OSRM is unavailable, falling back to straight line: {}", e.getMessage());
        }
        return estimate(from, to);
    }

    public RoutePath path(GeoPosition from, GeoPosition to) {
        try {
            OsrmResponse response = circuitBreaker.executeSupplier(() -> restClient.get()
                    .uri("/route/v1/" + properties.profile() + "/" + coordinate(from) + ";" + coordinate(to)
                            + "?overview=full&geometries=geojson")
                    .retrieve()
                    .body(OsrmResponse.class));
            if (response != null && "Ok".equals(response.code()) && response.routes() != null
                    && !response.routes().isEmpty() && response.routes().getFirst().hasGeometry()) {
                OsrmRoute best = response.routes().getFirst();
                return new RoutePath(Polyline.of(best.geometry().positions()), (int) Math.round(best.duration()),
                        Route.RouteSource.OSRM);
            }
            log.info("OSRM returned no geometry, courier goes by straight line: {}",
                    response == null ? null : response.code());
        } catch (RestClientException | CallNotPermittedException e) {
            log.warn("OSRM is unavailable, courier goes by straight line: {}", e.getMessage());
        }
        Route estimate = estimate(from, to);
        return new RoutePath(Polyline.straight(from, to), estimate.durationSeconds(), Route.RouteSource.STRAIGHT_LINE);
    }

    private Route estimate(GeoPosition from, GeoPosition to) {
        double roadMeters = straightLine.meters(from, to) * properties.fallbackDetourFactor();
        double seconds = roadMeters / (properties.fallbackSpeedKmh() * 1000 / 3600);
        return new Route((int) Math.round(roadMeters), (int) Math.round(seconds), Route.RouteSource.STRAIGHT_LINE);
    }

    private static String coordinate(GeoPosition position) {
        return String.format(Locale.ROOT, "%.6f,%.6f", position.longitude(), position.latitude());
    }

    record OsrmResponse(String code, List<OsrmRoute> routes) {
    }

    record OsrmRoute(double distance, double duration, OsrmGeometry geometry) {

        boolean hasGeometry() {
            return geometry != null && geometry.coordinates() != null && geometry.coordinates().size() > 1;
        }
    }

    record OsrmGeometry(List<List<Double>> coordinates) {

        List<GeoPosition> positions() {
            return coordinates.stream().map(pair -> new GeoPosition(pair.get(1), pair.get(0))).toList();
        }
    }
}
