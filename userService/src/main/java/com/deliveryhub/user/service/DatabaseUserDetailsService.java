package com.deliveryhub.user.service;

import com.deliveryhub.user.entity.User;
import com.deliveryhub.user.repository.UserRepository;
import com.deliveryhub.user.security.AppUserPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findUserByEmail(User.normalizeEmail(email))
                .map(AppUserPrincipal::of)
                .orElseThrow(() -> new UsernameNotFoundException("User with email: " + email + " was not found"));
    }
}
