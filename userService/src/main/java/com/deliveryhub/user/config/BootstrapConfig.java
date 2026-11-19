package com.deliveryhub.user.config;

import com.deliveryhub.user.service.BootstrapProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BootstrapProperties.class)
public class BootstrapConfig {
}
