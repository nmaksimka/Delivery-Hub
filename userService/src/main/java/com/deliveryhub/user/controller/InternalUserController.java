package com.deliveryhub.user.controller;

import com.deliveryhub.user.dto.UserContactDto;
import com.deliveryhub.user.service.UserRoles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalUserController {

    private final UserRoles userRoles;

    public InternalUserController(UserRoles userRoles) {
        this.userRoles = userRoles;
    }

    @GetMapping("/internal/users/{userId}/contact")
    public UserContactDto contact(@PathVariable Long userId) {
        return userRoles.contactOf(userId);
    }
}
