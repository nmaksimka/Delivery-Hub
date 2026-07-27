package com.deliveryhub.restaurant.storage;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Set;

@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(
        @DefaultValue("http://localhost:3900") String endpoint,
        @DefaultValue("http://localhost:3900") String publicEndpoint,
        @DefaultValue("garage") String region,
        @DefaultValue("images") String bucket,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @DefaultValue("12h") Duration presignTtl,
        @DefaultValue("5MB") DataSize maxImageSize,
        @DefaultValue("2s") Duration downloadConnectTimeout,
        @DefaultValue("10s") Duration downloadReadTimeout,
        @DefaultValue("30s") Duration downloadDeadline,
        @DefaultValue Mirror mirror) {

    public record Mirror(
            @DefaultValue("https") Set<String> allowedSchemes,
            @DefaultValue({"www.themealdb.com", "images.openfoodfacts.org"}) Set<String> allowedHosts) {
    }
}
