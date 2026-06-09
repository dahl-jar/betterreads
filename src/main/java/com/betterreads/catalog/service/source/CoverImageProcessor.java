package com.betterreads.catalog.service.source;

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
 * Re-encodes a downloaded cover to a clean, bounded JPEG.
 *
 * <p>Decoding the bytes through {@code ImageIO} and re-encoding a fresh JPEG is the sanitizing step:
 * bytes that are not a decodable image are rejected, and a real image is rewritten without its
 * original metadata or any trailing payload, so nothing the external host sent is stored verbatim.
 * The dimensions are read from the image header before the raster is decoded, so a small file
 * declaring huge dimensions is rejected without ever allocating its full pixel buffer.
 */
@Component
public class CoverImageProcessor {

    static final int MAX_DIMENSION = 800;

    private static final long MAX_PIXELS = 40_000_000L;

    private static final double JPEG_QUALITY = 0.8;

    private static final String JPEG_FORMAT = "jpg";

    private static final Logger LOG = LoggerFactory.getLogger(CoverImageProcessor.class);

    /**
     * Returns the cover re-encoded as a bounded JPEG, or empty when the bytes are not an image or
     * declare more pixels than the cap.
     *
     * <p>Decoding hostile external bytes can throw arbitrary unchecked exceptions from the image
     * readers (corrupt-stream index faults, negative array sizes), so they are caught and degraded to
     * empty rather than propagated.
     */
    // Checkstyle.IllegalCatch + PMD.AvoidCatchingGenericException: a malformed cover degrades to empty (see Javadoc)
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
            if (isTooManyPixels(reader.getWidth(0), reader.getHeight(0))) {
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

    private static boolean isTooManyPixels(final int width, final int height) {
        return (long) width * height > MAX_PIXELS;
    }
}
