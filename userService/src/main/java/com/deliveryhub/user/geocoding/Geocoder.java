package com.deliveryhub.user.geocoding;

import com.deliveryhub.user.entity.GeoPoint;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class Geocoder {

    private static final Logger log = LoggerFactory.getLogger(Geocoder.class);
    private static final ParameterizedTypeReference<List<NominatimPlace>> PLACES = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final NominatimProperties properties;
    private final Clock clock;
    private final Map<String, CachedLookup> cache;

    public Geocoder(RestClient restClient, RateLimiter rateLimiter, CircuitBreaker circuitBreaker,
                    NominatimProperties properties, Clock clock) {
        this.restClient = restClient;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.properties = properties;
        this.clock = clock;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedLookup> eldest) {
                return size() > properties.cacheSize();
            }
        };
    }

    public Optional<GeoPoint> locate(String street, String house) {
        String streetLine = house.strip() + " " + street.strip();
        String key = streetLine.toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        CachedLookup cached = cached(key, now);
        if (cached != null) {
            return cached.point();
        }
        Optional<GeoPoint> point = firstPlace(search(streetLine));
        remember(key, new CachedLookup(point, now.plus(properties.cacheTtl())));
        return point;
    }

    private List<NominatimPlace> search(String streetLine) {
        Supplier<List<NominatimPlace>> call = () -> restClient.get()
                .uri(builder -> builder.path("/search")
                        .queryParam("street", "{street}")
                        .queryParam("city", "{city}")
                        .queryParam("countrycodes", properties.countryCodes())
                        .queryParam("viewbox", properties.viewbox())
                        .queryParam("bounded", 1)
                        .queryParam("format", "jsonv2")
                        .queryParam("limit", 1)
                        .build(streetLine, properties.city()))
                .retrieve()
                .body(PLACES);
        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, RateLimiter.decorateSupplier(rateLimiter, call)).get();
        } catch (RestClientException | CallNotPermittedException | RequestNotPermitted e) {
            log.warn("Nominatim is unavailable: {}", e.getMessage());
            throw new GeocoderUnavailableException("Geocoding service is unavailable", e);
        }
    }

    private static Optional<GeoPoint> firstPlace(List<NominatimPlace> places) {
        if (places == null || places.isEmpty()) {
            return Optional.empty();
        }
        NominatimPlace place = places.getFirst();
        try {
            return Optional.of(new GeoPoint(new BigDecimal(place.lat()), new BigDecimal(place.lon())));
        } catch (NumberFormatException | NullPointerException e) {
            throw new GeocoderUnavailableException("Nominatim answered with unreadable coordinates", e);
        }
    }

    private synchronized CachedLookup cached(String key, Instant now) {
        CachedLookup cached = cache.get(key);
        if (cached == null || cached.expiresAt().isBefore(now)) {
            return null;
        }
        return cached;
    }

    private synchronized void remember(String key, CachedLookup lookup) {
        cache.put(key, lookup);
    }

    private record CachedLookup(Optional<GeoPoint> point, Instant expiresAt) {
    }
}
