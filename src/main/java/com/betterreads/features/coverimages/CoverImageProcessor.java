package com.betterreads.features.coverimages;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Re-encodes a downloaded cover to a bounded JPEG.
 *
 * <p>The external host can send anything, so bytes that don't decode as an image are rejected and a
 * real image is rewritten without its metadata or any trailing payload. The dimensions come from the
 * header before the raster is decoded, so a small file declaring huge dimensions is rejected before
 * its pixel buffer is allocated.
 */
@Component
class CoverImageProcessor {

    static final int MAX_DIMENSION = 800;

    private static final long MAX_PIXELS = 40_000_000L;

    private static final double JPEG_QUALITY = 0.8;

    private static final String JPEG_FORMAT = "jpg";

    private static final Logger LOG = LoggerFactory.getLogger(CoverImageProcessor.class);

    // Checkstyle.IllegalCatch + PMD.AvoidCatchingGenericException: image readers throw arbitrary unchecked errors
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    public Optional<byte[]> toCleanJpeg(final byte[] raw) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            if (input == null) {
                return Optional.empty();
            }
            return decodeWithinCap(input);
        } catch (IOException | RuntimeException ex) {
            LOG.debug("catalog.cover-process could not re-encode cover ({})", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<byte[]> decodeWithinCap(final ImageInputStream input) throws IOException {
        final Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            return Optional.empty();
        }
        final ImageReader reader = readers.next();
        try {
            reader.setInput(input);
            if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                return Optional.empty();
            }
            return Optional.of(reencode(reader.read(0)));
        } finally {
            reader.dispose();
        }
    }

    private static byte[] reencode(final BufferedImage decoded) throws IOException {
        final int target = Math.min(MAX_DIMENSION, Math.max(decoded.getWidth(), decoded.getHeight()));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(decoded)
            .size(target, target)
            .outputFormat(JPEG_FORMAT)
            .outputQuality(JPEG_QUALITY)
            .toOutputStream(out);
        return out.toByteArray();
    }
}
