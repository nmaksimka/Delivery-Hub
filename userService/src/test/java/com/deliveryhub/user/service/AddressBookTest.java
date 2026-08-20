package com.deliveryhub.user.service;

import com.deliveryhub.user.dto.AddressRequest;
import com.deliveryhub.user.entity.Address;
import com.deliveryhub.user.entity.AddressDetails;
import com.deliveryhub.user.entity.GeoPoint;
import com.deliveryhub.user.entity.GeoStatus;
import com.deliveryhub.user.exception.AddressLimitReachedException;
import com.deliveryhub.user.exception.GeocodingQuotaExceededException;
import com.deliveryhub.user.exception.AddressNotFoundException;
import com.deliveryhub.user.exception.AddressNotOnMapException;
import com.deliveryhub.user.geocoding.Geocoder;
import com.deliveryhub.user.geocoding.GeocodingQuota;
import com.deliveryhub.user.geocoding.GeocoderUnavailableException;
import com.deliveryhub.user.mapper.AddressMapperImpl;
import com.deliveryhub.user.repository.AddressRepository;
import com.deliveryhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddressBookTest {

    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-07-14T09:00:00Z");
    private static final GeoPoint LENINA = new GeoPoint(new BigDecimal("56.484645"), new BigDecimal("84.947649"));

    @Mock
    private AddressRepository addresses;

    @Mock
    private UserRepository users;

    @Mock
    private Geocoder geocoder;

    private AddressBook addressBook;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        addressBook = new AddressBook(addresses, users, new AddressMapperImpl(), geocoder, new GeocodingQuota(clock),
                TransactionOperations.withoutTransaction(), clock);
        when(users.lockById(USER_ID)).thenReturn(Optional.of(USER_ID));
        when(geocoder.locate(anyString(), anyString())).thenReturn(Optional.of(LENINA));
    }

    @Test
    void firstAddressBecomesPrimary() {
        when(addresses.countByUserId(USER_ID)).thenReturn(0L);
        when(addresses.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(addressBook.add(USER_ID, request()).primary()).isTrue();
    }

    @Test
    void nextAddressIsNotPrimary() {
        when(addresses.countByUserId(USER_ID)).thenReturn(3L);
        when(addresses.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(addressBook.add(USER_ID, request()).primary()).isFalse();
    }

    @Test
    void rejectsEleventhAddress() {
        when(addresses.countByUserId(USER_ID)).thenReturn((long) AddressBook.MAX_ADDRESSES);

        assertThatThrownBy(() -> addressBook.add(USER_ID, request())).isInstanceOf(AddressLimitReachedException.class);
        verify(addresses, never()).save(any());
        verify(geocoder, never()).locate(anyString(), anyString());
    }

    @Test
    void limitsAddressLookupsPerUser() {
        when(addresses.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));
        for (int i = 0; i < GeocodingQuota.LOOKUPS_PER_HOUR; i++) {
            addressBook.add(USER_ID, request());
        }

        assertThatThrownBy(() -> addressBook.add(USER_ID, request()))
                .isInstanceOf(GeocodingQuotaExceededException.class);
    }

    @Test
    void foreignAddressIsNotFound() {
        when(addresses.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressBook.change(USER_ID, 5L, request())).isInstanceOf(AddressNotFoundException.class);
        assertThatThrownBy(() -> addressBook.remove(USER_ID, 5L)).isInstanceOf(AddressNotFoundException.class);
        assertThatThrownBy(() -> addressBook.makePrimary(USER_ID, 5L)).isInstanceOf(AddressNotFoundException.class);
        verify(addresses, never()).clearPrimary(any());
    }

    @Test
    void storesCoordinatesFoundOnMap() {
        when(addresses.countByUserId(USER_ID)).thenReturn(0L);
        when(addresses.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));

        var created = addressBook.add(USER_ID, request());

        assertThat(created.geocoded()).isTrue();
        assertThat(created.latitude()).isEqualByComparingTo("56.484645");
        assertThat(created.longitude()).isEqualByComparingTo("84.947649");
    }

    @Test
    void rejectsAddressMissingOnMap() {
        when(geocoder.locate(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressBook.add(USER_ID, request())).isInstanceOf(AddressNotOnMapException.class);
        verify(addresses, never()).save(any());
    }

    @Test
    void savesAddressWithoutCoordinatesWhenGeocoderIsDown() {
        when(geocoder.locate(anyString(), anyString()))
                .thenThrow(new GeocoderUnavailableException("down", new RuntimeException()));
        when(addresses.countByUserId(USER_ID)).thenReturn(1L);
        when(addresses.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));

        var created = addressBook.add(USER_ID, request());

        assertThat(created.geocoded()).isFalse();
        assertThat(created.latitude()).isNull();
    }

    @Test
    void keepsCoordinatesWhenOnlyLabelChanges() {
        Address address = locatedAddress();
        when(addresses.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(address));

        var changed = addressBook.change(USER_ID, 5L, new AddressRequest("Работа", "Томск", "Ленина", "1", null, null, null, null));

        assertThat(changed.geocoded()).isTrue();
        verify(geocoder, never()).locate(anyString(), anyString());
    }

    @Test
    void movedAddressWaitsForGeocodingWhenGeocoderIsDown() {
        Address address = locatedAddress();
        when(addresses.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(address));
        when(geocoder.locate(anyString(), anyString()))
                .thenThrow(new GeocoderUnavailableException("down", new RuntimeException()));

        var changed = addressBook.change(USER_ID, 5L, new AddressRequest("Дом", "Томск", "Фрунзе", "20", null, null, null, null));

        assertThat(changed.geocoded()).isFalse();
        assertThat(address.getGeoStatus()).isEqualTo(GeoStatus.PENDING);
        assertThat(address.getLatitude()).isNull();
    }

    private static Address locatedAddress() {
        Address address = Address.create(USER_ID, new AddressDetails("Дом", "Томск", "Ленина", "1", null, null, null, null), true, NOW);
        address.locate(LENINA);
        return address;
    }

    private static AddressRequest request() {
        return new AddressRequest("Дом", "Томск", "Ленина", "1", null, null, null, null);
    }
}
