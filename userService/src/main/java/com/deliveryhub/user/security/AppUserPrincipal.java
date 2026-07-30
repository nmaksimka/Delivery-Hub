package com.deliveryhub.user.security;

import com.deliveryhub.user.entity.Role;
import com.deliveryhub.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record AppUserPrincipal(Long id, String email, String passwordHash, Role role) implements UserDetails {

    public static AppUserPrincipal of(User user) {
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPassword(), user.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[id=" + id + ", role=" + role + "]";
    }
}
