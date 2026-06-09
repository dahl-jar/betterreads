package com.betterreads.catalog.controller;

import java.time.Duration;

import com.betterreads.catalog.service.read.CoverImageService;
import com.betterreads.integration.minio.StoredImage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves book cover images from object storage so the browser fetches covers only from this API.
 */
@RestController
@RequestMapping("/api/v1/images")
@Tag(name = "Images", description = "Book cover images")
@SecurityRequirements
public class ImageController {

    private static final Duration CACHE_TTL = Duration.ofDays(365);

    private final CoverImageService coverImageService;

    public ImageController(final CoverImageService coverImageService) {
        this.coverImageService = coverImageService;
    }

    /**
     * Returns the cover image for the book key, or 404 when none can be resolved.
     *
     * @param key the book lookup key shared with detail and search
     * @param ifNoneMatch the client's cached ETag, answered with 304 when it still matches
     */
    @GetMapping("/covers/{key}")
    @Operation(summary = "Get a book cover image")
    public ResponseEntity<byte[]> cover(
        @PathVariable final String key,
        @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) final @Nullable String ifNoneMatch
    ) {
        final StoredImage image = coverImageService.loadCover(key).orElse(null);
        if (image == null) {
            return ResponseEntity.notFound().build();
        }
        final String etag = "\"" + DigestUtils.md5DigestAsHex(image.bytes()) + "\"";
        final CacheControl cacheControl = CacheControl.maxAge(CACHE_TTL).cachePublic().immutable();
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(etag)
                .cacheControl(cacheControl)
                .build();
        }
        return ResponseEntity.ok()
            .eTag(etag)
            .cacheControl(cacheControl)
            .contentType(MediaType.parseMediaType(image.contentType()))
            .body(image.bytes());
    }
}
