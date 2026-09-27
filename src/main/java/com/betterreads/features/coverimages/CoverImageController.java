package com.betterreads.features.coverimages;

import java.time.Duration;

import com.betterreads.images.Image;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

/** Serves book cover images by book key. */
@RestController
@RequestMapping("/api/v1/images")
@Tag(name = "Images", description = "Book cover images")
@SecurityRequirements
class CoverImageController {

    private static final Duration CACHE_TTL = Duration.ofDays(365);

    private final CoverImageService coverImageService;

    CoverImageController(final CoverImageService coverImageService) {
        this.coverImageService = coverImageService;
    }

    @GetMapping("/covers/{key}")
    @Operation(summary = "Get a book cover image")
    @ApiResponse(responseCode = "200", description = "The cover bytes",
        content = @Content(mediaType = MediaType.IMAGE_JPEG_VALUE,
            schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "304", description = "Cached copy is current", content = @Content)
    @ApiResponse(responseCode = "404", description = "No cover", content = @Content)
    public ResponseEntity<byte[]> cover(
        @PathVariable final String key,
        @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) final @Nullable String ifNoneMatch
    ) {
        return coverImageService.loadCover(key)
            .map(image -> respond(image, ifNoneMatch))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<byte[]> respond(final Image image, final @Nullable String ifNoneMatch) {
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
