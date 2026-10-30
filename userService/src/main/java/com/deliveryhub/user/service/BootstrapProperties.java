package com.deliveryhub.user.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.bootstrap.admin")
public record BootstrapProperties(String email, String password) {

    public boolean configured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
