package com.deliveryhub.gateway.limit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("app.request-body")
public record RequestBodyProperties(@DefaultValue("7MB") DataSize maxSize) {
}
