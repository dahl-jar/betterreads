package com.betterreads.clients.minio;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MinIO config bound from {@code minio.*}.
 *
 * @param bucket holds book images
 */
@Validated
@ConfigurationProperties(prefix = "minio")
record MinioProperties(
    @NotBlank String endpoint,
    @NotBlank String bucket,
    @NotBlank String accessKey,
    @NotBlank String secretKey
) { }
