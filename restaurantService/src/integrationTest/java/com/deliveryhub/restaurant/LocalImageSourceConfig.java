package com.deliveryhub.restaurant;

import com.deliveryhub.restaurant.storage.ImageSources;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;

import java.net.InetAddress;

@TestConfiguration(proxyBeanMethods = false)
public class LocalImageSourceConfig {

    @Bean
    @Primary
    ImageSources.HostResolver localhostAsPublicHost() {
        return host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")};
    }

    @Bean
    DynamicPropertyRegistrar localImageSource() {
        return registry -> {
            registry.add("app.storage.mirror.allowed-schemes", () -> "http");
            registry.add("app.storage.mirror.allowed-hosts", () -> "localhost");
        };
    }
}
