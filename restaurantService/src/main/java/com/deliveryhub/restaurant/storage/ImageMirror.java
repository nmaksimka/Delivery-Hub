package com.deliveryhub.restaurant.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Optional;

@Component
public class ImageMirror {

    private static final Logger log = LoggerFactory.getLogger(ImageMirror.class);

    private final ImageStorage imageStorage;
    private final ImageIntake imageIntake;
    private final ImageSources sources;
    private final StorageProperties properties;
    private final RestClient http;

    public ImageMirror(ImageStorage imageStorage, ImageIntake imageIntake, ImageSources sources,
                       StorageProperties properties, RestClient.Builder builder) {
        this.imageStorage = imageStorage;
        this.imageIntake = imageIntake;
        this.sources = sources;
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.downloadConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        requestFactory.setReadTimeout(properties.downloadReadTimeout());
        this.http = builder.requestFactory(requestFactory).build();
    }

    public Optional<String> mirror(String key, String sourceUrl) {
        if (sourceUrl == null) {
            return Optional.empty();
        }
        try {
            URI source = URI.create(sourceUrl);
            if (!sources.permits(source)) {
                log.warn("Image {} is not from an allowed source", sourceUrl);
                return Optional.empty();
            }
            if (imageStorage.exists(key)) {
                return Optional.of(key);
            }
            byte[] content = download(source);
            if (content == null) {
                return Optional.empty();
            }
            imageIntake.save(key, content);
            return Optional.of(key);
        } catch (RuntimeException e) {
            log.warn("Image {} was not copied to storage: {}", sourceUrl, e.toString());
            return Optional.empty();
        }
    }

    private byte[] download(URI source) {
        int limit = (int) properties.maxImageSize().toBytes();
        return http.get().uri(source).exchange((request, response) -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Image {} answered with status {}", source, response.getStatusCode().value());
                return null;
            }
            return response.getBody().readNBytes(limit + 1);
        });
    }
}
