package com.betterreads.images;

/**
 * Image bytes with their MIME type, read from object storage or fetched from a cover URL.
 *
 * @param contentType e.g. {@code image/jpeg}
 */
public record Image(byte[] bytes, String contentType) {

    public Image(final byte[] bytes, final String contentType) {
        this.bytes = bytes.clone();
        this.contentType = contentType;
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
