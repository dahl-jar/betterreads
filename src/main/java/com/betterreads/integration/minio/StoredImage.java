package com.betterreads.integration.minio;

/**
 * An image read from object storage.
 *
 * @param bytes the image content
 * @param contentType the stored MIME type, e.g. {@code image/jpeg}
 */
public record StoredImage(byte[] bytes, String contentType) {

    public StoredImage(final byte[] bytes, final String contentType) {
        this.bytes = bytes.clone();
        this.contentType = contentType;
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
