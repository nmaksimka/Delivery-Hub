package com.deliveryhub.user.controller;

import com.deliveryhub.user.dto.ChangeRoleRequest;
import com.deliveryhub.user.dto.UserDto;
import com.deliveryhub.user.exception.AdminOnlyException;
import com.deliveryhub.user.service.UserRoles;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private static final String ADMIN = "ADMIN";

    private final UserRoles userRoles;

    public UserAdminController(UserRoles userRoles) {
        this.userRoles = userRoles;
    }

    @GetMapping
    public UserDto findByEmail(@RequestParam("email") String email,
                               @RequestHeader(name = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return userRoles.findByEmail(email);
    }

    @PutMapping("/{id}/role")
    public UserDto changeRole(@PathVariable Long id,
                              @Valid @RequestBody ChangeRoleRequest request,
                              @RequestHeader(name = "X-User-Id", required = false) Long adminId,
                              @RequestHeader(name = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return userRoles.changeRole(id, request.role(), adminId);
    }

    private static void requireAdmin(String role) {
        if (!ADMIN.equals(role)) {
            throw new AdminOnlyException();
        }
    }
}
