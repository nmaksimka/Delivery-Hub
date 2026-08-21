package com.deliveryhub.user.controller;

import com.deliveryhub.user.dto.AddressDto;
import com.deliveryhub.user.service.AddressBook;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalAddressController {

    private final AddressBook addressBook;

    public InternalAddressController(AddressBook addressBook) {
        this.addressBook = addressBook;
    }

    @GetMapping("/internal/users/{userId}/addresses/{addressId}")
    public AddressDto address(@PathVariable Long userId, @PathVariable Long addressId) {
        return addressBook.get(userId, addressId);
    }
}
