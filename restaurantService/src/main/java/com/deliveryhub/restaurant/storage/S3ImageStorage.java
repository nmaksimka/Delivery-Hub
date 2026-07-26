package com.deliveryhub.restaurant.storage;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner.AuthLocation;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.utils.http.SdkHttpUtils;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

public class S3ImageStorage implements ImageStorage {

    private static final String SERVICE = "s3";

    private final S3Client client;
    private final AwsCredentials credentials;
    private final StorageProperties properties;
    private final Clock clock;
    private final AwsV4HttpSigner signer = AwsV4HttpSigner.create();

    public S3ImageStorage(S3Client client, AwsCredentials credentials, StorageProperties properties, Clock clock) {
        this.client = client;
        this.credentials = credentials;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        client.putObject(PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build(), RequestBody.fromBytes(content));
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
    }

    @Override
    public String readUrl(String key) {
        SdkHttpRequest request = SdkHttpRequest.builder()
                .method(SdkHttpMethod.GET)
                .uri(URI.create(properties.publicEndpoint() + "/" + properties.bucket() + "/"
                        + SdkHttpUtils.urlEncodeIgnoreSlashes(key)))
                .build();
        Clock signingClock = Clock.fixed(windowStart(clock.instant()), ZoneOffset.UTC);
        return signer.sign(signing -> signing
                        .identity(credentials)
                        .request(request)
                        .putProperty(AwsV4FamilyHttpSigner.SERVICE_SIGNING_NAME, SERVICE)
                        .putProperty(AwsV4HttpSigner.REGION_NAME, properties.region())
                        .putProperty(AwsV4FamilyHttpSigner.AUTH_LOCATION, AuthLocation.QUERY_STRING)
                        .putProperty(AwsV4FamilyHttpSigner.EXPIRATION_DURATION, properties.presignTtl())
                        .putProperty(AwsV4FamilyHttpSigner.PAYLOAD_SIGNING_ENABLED, false)
                        .putProperty(AwsV4FamilyHttpSigner.DOUBLE_URL_ENCODE, false)
                        .putProperty(AwsV4FamilyHttpSigner.NORMALIZE_PATH, false)
                        .putProperty(HttpSigner.SIGNING_CLOCK, signingClock))
                .request()
                .getUri()
                .toString();
    }

    private Instant windowStart(Instant now) {
        long window = signingWindow().toMillis();
        return Instant.ofEpochMilli(Math.floorDiv(now.toEpochMilli(), window) * window);
    }

    private Duration signingWindow() {
        return properties.presignTtl().dividedBy(2);
    }
}
