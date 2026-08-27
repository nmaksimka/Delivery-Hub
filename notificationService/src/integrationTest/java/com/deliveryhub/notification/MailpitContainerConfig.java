package com.deliveryhub.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@TestConfiguration(proxyBeanMethods = false)
public class MailpitContainerConfig {

    static final int SMTP_PORT = 1025;
    static final int API_PORT = 8025;

    @Bean
    GenericContainer<?> mailpit() {
        return new GenericContainer<>("axllent/mailpit:v1.29.7")
                .withExposedPorts(SMTP_PORT, API_PORT)
                .withEnv("MP_SMTP_DISABLE_RDNS", "true")
                .waitingFor(Wait.forHttp("/readyz").forPort(API_PORT));
    }

    @Bean
    DynamicPropertyRegistrar mailpitSmtp(GenericContainer<?> mailpit) {
        return registry -> {
            registry.add("spring.mail.host", mailpit::getHost);
            registry.add("spring.mail.port", () -> mailpit.getMappedPort(SMTP_PORT));
        };
    }
}
