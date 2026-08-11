package com.deliveryhub.user.controller;

import com.deliveryhub.user.dto.AddressDto;
import com.deliveryhub.user.dto.AddressRequest;
import com.deliveryhub.user.service.AddressBook;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/addresses")
public class AddressController {

    private static final String USER_ID = "X-User-Id";

    private final AddressBook addressBook;

    public AddressController(AddressBook addressBook) {
        this.addressBook = addressBook;
    }

    @GetMapping
    public List<AddressDto> list(@RequestHeader(USER_ID) Long userId) {
        return addressBook.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressDto create(@RequestHeader(USER_ID) Long userId, @Valid @RequestBody AddressRequest request) {
        return addressBook.add(userId, request);
    }

    @PutMapping("/{id}")
    public AddressDto change(@RequestHeader(USER_ID) Long userId, @PathVariable Long id,
                             @Valid @RequestBody AddressRequest request) {
        return addressBook.change(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(USER_ID) Long userId, @PathVariable Long id) {
        addressBook.remove(userId, id);
    }

    @PostMapping("/{id}/default")
    public AddressDto makeDefault(@RequestHeader(USER_ID) Long userId, @PathVariable Long id) {
        return addressBook.makePrimary(userId, id);
    }
}
