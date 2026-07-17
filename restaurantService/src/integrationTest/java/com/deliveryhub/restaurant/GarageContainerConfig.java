package com.deliveryhub.restaurant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class GarageContainerConfig {

    @Bean
    GarageContainer garage() {
        return new GarageContainer();
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(GarageContainer garage) {
        return registry -> {
            registry.add("app.storage.endpoint", garage::s3Endpoint);
            registry.add("app.storage.public-endpoint", garage::s3Endpoint);
            registry.add("app.storage.access-key", () -> GarageContainer.ACCESS_KEY);
            registry.add("app.storage.secret-key", () -> GarageContainer.SECRET_KEY);
            registry.add("app.storage.bucket", () -> GarageContainer.BUCKET);
        };
    }
}
