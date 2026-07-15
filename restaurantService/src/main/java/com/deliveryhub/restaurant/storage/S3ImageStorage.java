package com.deliveryhub.restaurant.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public class S3ImageStorage implements ImageStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public S3ImageStorage(S3Client client, S3Presigner presigner, StorageProperties properties) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
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
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(properties.presignTtl())
                .getObjectRequest(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build())
                .build();
        return presigner.presignGetObject(request).url().toString();
    }
}
