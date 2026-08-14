package com.deliveryhub.user.service;

import com.deliveryhub.user.entity.Address;
import com.deliveryhub.user.entity.GeoPoint;
import com.deliveryhub.user.entity.GeoStatus;
import com.deliveryhub.user.geocoding.Geocoder;
import com.deliveryhub.user.geocoding.GeocoderUnavailableException;
import com.deliveryhub.user.geocoding.NominatimProperties;
import com.deliveryhub.user.repository.AddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.Optional;

@Component
public class GeocodingRetry {

    private static final Logger log = LoggerFactory.getLogger(GeocodingRetry.class);

    private final AddressRepository addresses;
    private final Geocoder geocoder;
    private final TransactionOperations transactions;
    private final NominatimProperties properties;

    public GeocodingRetry(AddressRepository addresses, Geocoder geocoder, TransactionOperations transactions,
                          NominatimProperties properties) {
        this.addresses = addresses;
        this.geocoder = geocoder;
        this.transactions = transactions;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.geocoding.retry-interval}", initialDelayString = "${app.geocoding.retry-initial-delay:1m}")
    public void retryPending() {
        List<Address> pending = addresses.findByGeoStatusOrderByUpdatedAtAscIdAsc(
                GeoStatus.PENDING, Limit.of(properties.retryBatchSize()));
        int located = 0;
        for (Address address : pending) {
            Optional<GeoPoint> point;
            try {
                point = geocoder.locate(address.getStreet(), address.getHouse());
            } catch (GeocoderUnavailableException e) {
                log.info("Geocoding retry postponed, {} addresses still pending", pending.size() - located);
                return;
            }
            transactions.executeWithoutResult(status -> apply(address, point));
            located++;
        }
        if (located > 0) {
            log.info("Geocoding retry processed {} addresses", located);
        }
    }

    private void apply(Address snapshot, Optional<GeoPoint> point) {
        addresses.findById(snapshot.getId())
                .filter(address -> address.getGeoStatus() == GeoStatus.PENDING)
                .filter(address -> address.mapQuery().equals(snapshot.mapQuery()))
                .ifPresent(address -> point.ifPresentOrElse(address::locate, address::markNotOnMap));
    }
}
