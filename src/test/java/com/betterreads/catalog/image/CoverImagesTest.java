package com.betterreads.catalog.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The served cover URL is absolute on the public API origin when the book has a cover, and null when
 * it has none, so the SPA loads covers from the API host and a coverless book sends no broken link.
 */
class CoverImagesTest {

    private static final String KEY = "OL1W";

    private static final String SOURCE_URL = "https://covers.example.org/1.jpg";

    private static final String API_BASE = "https://api.betterreadsapp.com";

    private static final String EXPECTED_URL =
        API_BASE + "/api/v1/images/covers/OL1W?v=" + CoverVersion.of(SOURCE_URL);

    private final CoverImages coverImages = new CoverImages(new ImageProperties(API_BASE));

    @Test
    @DisplayName("a book with a cover gets the absolute image endpoint url with a version token")
    void servedUrlForBookWithCover() {
        assertThat(coverImages.servedUrl(KEY, SOURCE_URL)).isEqualTo(EXPECTED_URL);
    }

    @Test
    @DisplayName("a different cover url yields a different version token")
    void differentCoverUrlChangesVersion() {
        final String first = coverImages.servedUrl(KEY, SOURCE_URL);
        final String second = coverImages.servedUrl(KEY, "https://covers.example.org/2.jpg");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("a trailing slash on the base url is not doubled")
    void trailingSlashNotDoubled() {
        final CoverImages withSlash = new CoverImages(new ImageProperties(API_BASE + "/"));

        assertThat(withSlash.servedUrl(KEY, SOURCE_URL)).isEqualTo(EXPECTED_URL);
    }

    @Test
    @DisplayName("a book with no cover gets null")
    void nullWhenNoCover() {
        assertThat(coverImages.servedUrl(KEY, null)).isNull();
    }

    @Test
    @DisplayName("a book with a blank cover gets null")
    void nullWhenBlankCover() {
        assertThat(coverImages.servedUrl(KEY, "  ")).isNull();
    }
}
