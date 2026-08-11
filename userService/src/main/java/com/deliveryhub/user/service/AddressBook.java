package com.deliveryhub.user.service;

import com.deliveryhub.user.dto.AddressDto;
import com.deliveryhub.user.dto.AddressRequest;
import com.deliveryhub.user.entity.Address;
import com.deliveryhub.user.exception.AddressLimitReachedException;
import com.deliveryhub.user.exception.AddressNotFoundException;
import com.deliveryhub.user.exception.UserNotFoundException;
import com.deliveryhub.user.mapper.AddressMapper;
import com.deliveryhub.user.repository.AddressRepository;
import com.deliveryhub.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class AddressBook {

    public static final int MAX_ADDRESSES = 10;

    private final AddressRepository addresses;
    private final UserRepository users;
    private final AddressMapper mapper;
    private final Clock clock;

    public AddressBook(AddressRepository addresses, UserRepository users, AddressMapper mapper, Clock clock) {
        this.addresses = addresses;
        this.users = users;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AddressDto> list(Long userId) {
        return addresses.findByUserIdOrderByPrimaryDescCreatedAtAscIdAsc(userId).stream().map(mapper::toDto).toList();
    }

    @Transactional
    public AddressDto add(Long userId, AddressRequest request) {
        lockUser(userId);
        long existing = addresses.countByUserId(userId);
        if (existing >= MAX_ADDRESSES) {
            throw new AddressLimitReachedException(MAX_ADDRESSES);
        }
        Address address = Address.create(userId, mapper.toDetails(request), existing == 0, clock.instant());
        return mapper.toDto(addresses.save(address));
    }

    @Transactional
    public AddressDto change(Long userId, Long addressId, AddressRequest request) {
        Address address = owned(userId, addressId);
        address.change(mapper.toDetails(request), clock.instant());
        return mapper.toDto(address);
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

    private Address owned(Long userId, Long addressId) {
        return addresses.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));
    }

    private void lockUser(Long userId) {
        users.lockById(userId).orElseThrow(() -> UserNotFoundException.byId(userId));
    }
}
