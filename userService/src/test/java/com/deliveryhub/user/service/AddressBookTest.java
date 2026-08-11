package com.deliveryhub.user.service;

import com.deliveryhub.user.dto.AddressRequest;
import com.deliveryhub.user.entity.Address;
import com.deliveryhub.user.exception.AddressLimitReachedException;
import com.deliveryhub.user.exception.AddressNotFoundException;
import com.deliveryhub.user.mapper.AddressMapperImpl;
import com.deliveryhub.user.repository.AddressRepository;
import com.deliveryhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressBookTest {

    private static final Long USER_ID = 7L;

    @Mock
    private AddressRepository addresses;

    @Mock
    private UserRepository users;

    private AddressBook addressBook;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-14T09:00:00Z"), ZoneOffset.UTC);
        addressBook = new AddressBook(addresses, users, new AddressMapperImpl(), clock);
        when(users.lockById(USER_ID)).thenReturn(Optional.of(USER_ID));
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
    }

    @Test
    void foreignAddressIsNotFound() {
        when(addresses.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressBook.change(USER_ID, 5L, request())).isInstanceOf(AddressNotFoundException.class);
        assertThatThrownBy(() -> addressBook.remove(USER_ID, 5L)).isInstanceOf(AddressNotFoundException.class);
        assertThatThrownBy(() -> addressBook.makePrimary(USER_ID, 5L)).isInstanceOf(AddressNotFoundException.class);
        verify(addresses, never()).clearPrimary(any());
    }

    private static AddressRequest request() {
        return new AddressRequest("Дом", "Томск", "Ленина", "1", null, null, null, null);
    }
}
