package com.deliveryhub.search.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.search")
public record SearchProperties(
        @DefaultValue("catalog") String index,
        @DefaultValue("50") int maxPageSize,
        @DefaultValue("8") int suggestions) {
}
