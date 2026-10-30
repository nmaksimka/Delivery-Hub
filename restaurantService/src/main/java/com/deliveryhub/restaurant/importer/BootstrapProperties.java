package com.deliveryhub.restaurant.importer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.bootstrap")
public record BootstrapProperties(@DefaultValue("false") boolean importMealsOnEmpty) {
}
