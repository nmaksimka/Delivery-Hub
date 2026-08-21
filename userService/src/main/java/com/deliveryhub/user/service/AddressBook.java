package com.deliveryhub.user.service;

import com.deliveryhub.user.dto.AddressDto;
import com.deliveryhub.user.dto.AddressRequest;
import com.deliveryhub.user.entity.Address;
import com.deliveryhub.user.entity.AddressDetails;
import com.deliveryhub.user.entity.GeoPoint;
import com.deliveryhub.user.exception.AddressLimitReachedException;
import com.deliveryhub.user.exception.AddressNotFoundException;
import com.deliveryhub.user.exception.AddressNotOnMapException;
import com.deliveryhub.user.exception.UserNotFoundException;
import com.deliveryhub.user.geocoding.Geocoder;
import com.deliveryhub.user.geocoding.GeocoderUnavailableException;
import com.deliveryhub.user.geocoding.GeocodingQuota;
import com.deliveryhub.user.mapper.AddressMapper;
import com.deliveryhub.user.repository.AddressRepository;
import com.deliveryhub.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public class AddressBook {

    public static final int MAX_ADDRESSES = 10;

    private final AddressRepository addresses;
    private final UserRepository users;
    private final AddressMapper mapper;
    private final Geocoder geocoder;
    private final GeocodingQuota quota;
    private final TransactionOperations transactions;
    private final Clock clock;

    public AddressBook(AddressRepository addresses, UserRepository users, AddressMapper mapper, Geocoder geocoder,
                       GeocodingQuota quota, TransactionOperations transactions, Clock clock) {
        this.addresses = addresses;
        this.users = users;
        this.mapper = mapper;
        this.geocoder = geocoder;
        this.quota = quota;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AddressDto> list(Long userId) {
        return addresses.findByUserIdOrderByPrimaryDescCreatedAtAscIdAsc(userId).stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AddressDto get(Long userId, Long addressId) {
        return mapper.toDto(owned(userId, addressId));
    }

    public AddressDto add(Long userId, AddressRequest request) {
        AddressDetails details = mapper.toDetails(request);
        if (addresses.countByUserId(userId) >= MAX_ADDRESSES) {
            throw new AddressLimitReachedException(MAX_ADDRESSES);
        }
        Optional<GeoPoint> point = locate(userId, details);
        return transactions.execute(status -> {
            lockUser(userId);
            long existing = addresses.countByUserId(userId);
            if (existing >= MAX_ADDRESSES) {
                throw new AddressLimitReachedException(MAX_ADDRESSES);
            }
            Address address = Address.create(userId, details, existing == 0, clock.instant());
            point.ifPresent(address::locate);
            return mapper.toDto(addresses.save(address));
        });
    }

    public AddressDto change(Long userId, Long addressId, AddressRequest request) {
        AddressDetails details = mapper.toDetails(request);
        Address current = owned(userId, addressId);
        boolean alreadyLocated = current.isGeocoded()
                && current.mapQuery().equals(Address.mapQueryOf(details.street(), details.house()));
        Consumer<Address> placeOnMap = alreadyLocated ? address -> { } : placement(locate(userId, details));
        return transactions.execute(status -> {
            Address address = owned(userId, addressId);
            address.change(details, clock.instant());
            placeOnMap.accept(address);
            return mapper.toDto(address);
        });
    }

    private static Consumer<Address> placement(Optional<GeoPoint> point) {
        return address -> point.ifPresentOrElse(address::locate, address::awaitGeocoding);
    }

    @Transactional
    public void remove(Long userId, Long addressId) {
        lockUser(userId);
        Address address = owned(userId, addressId);
        boolean wasPrimary = address.isPrimary();
        addresses.delete(address);
        addresses.flush();
        if (wasPrimary) {
            addresses.findFirstByUserIdOrderByCreatedAtAscIdAsc(userId)
                    .ifPresent(next -> next.markPrimary(clock.instant()));
        }
    }

    @Transactional
    public AddressDto makePrimary(Long userId, Long addressId) {
        lockUser(userId);
        owned(userId, addressId);
        addresses.clearPrimary(userId);
        Address address = owned(userId, addressId);
        address.markPrimary(clock.instant());
        return mapper.toDto(address);
    }

    private Optional<GeoPoint> locate(Long userId, AddressDetails details) {
        quota.consume(userId);
        try {
            return Optional.of(geocoder.locate(details.street(), details.house())
                    .orElseThrow(() -> new AddressNotOnMapException(details.street(), details.house())));
        } catch (GeocoderUnavailableException e) {
            return Optional.empty();
        }
    }

    private Address owned(Long userId, Long addressId) {
        return addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));
    }

    private void lockUser(Long userId) {
        users.lockById(userId).orElseThrow(() -> UserNotFoundException.byId(userId));
    }
}
