package com.deliveryhub.user.service;

import com.deliveryhub.user.entity.User;
import com.deliveryhub.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ADMIN_NAME = "Администратор";

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminBootstrap(BootstrapProperties properties,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createAdminIfAbsent() {
        if (!properties.configured()) {
            return;
        }
        String email = User.normalizeEmail(properties.email());
        if (userRepository.existsByEmail(email)) {
            return;
        }
        try {
            User admin = userRepository.saveAndFlush(User.admin(
                    email, passwordEncoder.encode(properties.password()), ADMIN_NAME, clock.instant()));
            log.info("Bootstrap admin created id={}", admin.getId());
        } catch (DataIntegrityViolationException e) {
            log.info("Bootstrap admin already exists");
        }
    }
}
