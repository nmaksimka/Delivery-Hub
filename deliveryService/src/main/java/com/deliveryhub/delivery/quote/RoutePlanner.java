package com.deliveryhub.delivery.quote;

import com.deliveryhub.delivery.entity.GeoPosition;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class RoutePlanner {

    private static final Logger log = LoggerFactory.getLogger(RoutePlanner.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int CACHE_SIZE = 2000;

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;
    private final StraightLine straightLine;
    private final RoutingProperties properties;
    private final Clock clock;
    private final Map<String, Cached<Route>> routes = new ConcurrentHashMap<>();
    private final Map<String, Cached<RoutePath>> paths = new ConcurrentHashMap<>();

    public RoutePlanner(RestClient restClient, CircuitBreaker circuitBreaker, RateLimiter rateLimiter,
                        StraightLine straightLine, RoutingProperties properties, Clock clock) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
        this.rateLimiter = rateLimiter;
        this.straightLine = straightLine;
        this.properties = properties;
        this.clock = clock;
    }

    public Route route(GeoPosition from, GeoPosition to) {
        String key = key(from, to);
        Route cached = fresh(routes, key);
        if (cached != null) {
            return cached;
        }
        try {
            OsrmResponse response = call(from, to, "?overview=false");
            if (response != null && "Ok".equals(response.code()) && response.routes() != null
                    && !response.routes().isEmpty()) {
                OsrmRoute best = response.routes().getFirst();
                Route route = new Route((int) Math.round(best.distance()), (int) Math.round(best.duration()),
                        Route.RouteSource.OSRM);
                remember(routes, key, route);
                return route;
            }
            log.info("OSRM found no route, falling back to straight line: {}", response == null ? null : response.code());
        } catch (RestClientException | CallNotPermittedException | RequestNotPermitted e) {
            log.warn("OSRM is unavailable, falling back to straight line: {}", e.getClass().getSimpleName());
        }
        return estimate(from, to);
    }

    public RoutePath path(GeoPosition from, GeoPosition to) {
        String key = key(from, to);
        RoutePath cached = fresh(paths, key);
        if (cached != null) {
            return cached;
        }
        try {
            OsrmResponse response = call(from, to, "?overview=full&geometries=geojson");
            if (response != null && "Ok".equals(response.code()) && response.routes() != null
                    && !response.routes().isEmpty() && response.routes().getFirst().hasGeometry()) {
                OsrmRoute best = response.routes().getFirst();
                return rememberPath(key, readPath(best)).orElseGet(() -> straightPath(from, to));
            }
            log.info("OSRM returned no geometry, courier goes by straight line: {}",
                    response == null ? null : response.code());
        } catch (RestClientException | CallNotPermittedException | RequestNotPermitted e) {
            log.warn("OSRM is unavailable, courier goes by straight line: {}", e.getClass().getSimpleName());
        }
        return straightPath(from, to);
    }

    private OsrmResponse call(GeoPosition from, GeoPosition to, String query) {
        Supplier<OsrmResponse> request = () -> restClient.get()
                .uri("/route/v1/" + properties.profile() + "/" + coordinate(from) + ";" + coordinate(to) + query)
                .retrieve()
                .body(OsrmResponse.class);
        return CircuitBreaker.decorateSupplier(circuitBreaker, RateLimiter.decorateSupplier(rateLimiter, request)).get();
    }

    private <T> T fresh(Map<String, Cached<T>> cache, String key) {
        Cached<T> cached = cache.get(key);
        if (cached == null || !clock.instant().isBefore(cached.expiresAt())) {
            return null;
        }
        return cached.value();
    }

    private <T> void remember(Map<String, Cached<T>> cache, String key, T value) {
        if (cache.size() >= CACHE_SIZE) {
            cache.clear();
        }
        cache.put(key, new Cached<>(value, clock.instant().plus(CACHE_TTL)));
    }

    private Optional<RoutePath> rememberPath(String key, Optional<RoutePath> path) {
        path.ifPresent(value -> remember(paths, key, value));
        return path;
    }

    private static Optional<RoutePath> readPath(OsrmRoute route) {
        try {
            return Optional.of(new RoutePath(Polyline.of(route.geometry().positions()),
                    (int) Math.round(route.duration()), Route.RouteSource.OSRM));
        } catch (RuntimeException e) {
            log.warn("OSRM geometry is unreadable, courier goes by straight line: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String key(GeoPosition from, GeoPosition to) {
        return String.format(Locale.ROOT, "%.5f,%.5f;%.5f,%.5f",
                from.latitude(), from.longitude(), to.latitude(), to.longitude());
    }

    private RoutePath straightPath(GeoPosition from, GeoPosition to) {
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

    private record Cached<T>(T value, Instant expiresAt) {
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
