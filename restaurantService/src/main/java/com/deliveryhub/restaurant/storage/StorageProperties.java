package com.deliveryhub.restaurant.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties("app.storage")
public record StorageProperties(
        @DefaultValue("http://localhost:3900") String endpoint,
        @DefaultValue("http://localhost:3900") String publicEndpoint,
        @DefaultValue("garage") String region,
        @DefaultValue("images") String bucket,
        @DefaultValue("GK0123456789abcdef01234567") String accessKey,
        @DefaultValue("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef") String secretKey,
        @DefaultValue("10m") Duration presignTtl,
        @DefaultValue("5MB") DataSize maxImageSize,
        @DefaultValue("2s") Duration downloadConnectTimeout,
        @DefaultValue("10s") Duration downloadReadTimeout) {
}
