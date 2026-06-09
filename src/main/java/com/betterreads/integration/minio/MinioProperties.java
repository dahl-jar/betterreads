package com.betterreads.integration.minio;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MinIO object-storage config bound from {@code minio.*}.
 *
 * @param endpoint the S3 API endpoint, e.g. {@code http://minio.betterreads.svc.cluster.local:9000}
 * @param bucket the bucket holding book images
 * @param accessKey the scoped access key
 * @param secretKey the scoped secret key
 */
@Validated
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
    @NotBlank String endpoint,
    @NotBlank String bucket,
    @NotBlank String accessKey,
    @NotBlank String secretKey
) { }
