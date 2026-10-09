package com.betterreads.features.coverimages;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Re-encodes a downloaded cover to a bounded JPEG.
 *
 * <p>The external host can send anything, so bytes that don't decode as an image are rejected and a
 * real image is rewritten without its metadata or any trailing payload.
 */
@Component
class CoverImageProcessor {

    static final int MAX_DIMENSION = 800;

    private static final double JPEG_QUALITY = 0.8;

    private static final String JPEG_FORMAT = "jpg";

    private static final Logger LOG = LoggerFactory.getLogger(CoverImageProcessor.class);

    public Optional<byte[]> toCleanJpeg(final byte[] raw) {
        return ImageDecoder.decode(raw).flatMap(CoverImageProcessor::reencode);
    }

    // Checkstyle.IllegalCatch + PMD.AvoidCatchingGenericException: the encoder throws arbitrary unchecked errors
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    private static Optional<byte[]> reencode(final BufferedImage decoded) {
        final int target = Math.min(MAX_DIMENSION, Math.max(decoded.getWidth(), decoded.getHeight()));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Thumbnails.of(decoded)
                .size(target, target)
                .outputFormat(JPEG_FORMAT)
                .outputQuality(JPEG_QUALITY)
                .toOutputStream(out);
            return Optional.of(out.toByteArray());
        } catch (IOException | RuntimeException ex) {
            LOG.debug("catalog.cover-process could not re-encode cover ({})", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
