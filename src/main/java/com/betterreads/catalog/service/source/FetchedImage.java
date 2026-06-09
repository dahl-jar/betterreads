package com.betterreads.catalog.service.source;

/**
 * Image bytes fetched from an external cover URL.
 *
 * @param bytes the downloaded content
 * @param contentType the response content type, e.g. {@code image/jpeg}
 */
public record FetchedImage(byte[] bytes, String contentType) {

    public FetchedImage(final byte[] bytes, final String contentType) {
        this.bytes = bytes.clone();
        this.contentType = contentType;
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
