package com.betterreads.catalog.service.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The processor re-encodes a real image to a clean JPEG and rejects bytes that do not decode as an
 * image, so a payload disguised as a cover is dropped rather than stored.
 */
class CoverImageProcessorTest {

    private static final int LARGE_DIMENSION = 1600;

    private static final int SMALL_DIMENSION = 200;

    private final CoverImageProcessor processor = new CoverImageProcessor();

    @Test
    @DisplayName("an oversized image is re-encoded down to the max dimension")
    void resizesLargeImage() {
        final byte[] large = pngOf(LARGE_DIMENSION, LARGE_DIMENSION);

        final Optional<byte[]> jpeg = processor.toCleanJpeg(large);

        assertThat(jpeg).isPresent();
        final BufferedImage decoded = read(jpeg.orElseThrow());
        assertThat(decoded.getWidth()).isLessThanOrEqualTo(CoverImageProcessor.MAX_DIMENSION);
        assertThat(decoded.getHeight()).isLessThanOrEqualTo(CoverImageProcessor.MAX_DIMENSION);
    }

    @Test
    @DisplayName("a small image is kept rather than upscaled")
    void keepsSmallImage() {
        final byte[] small = pngOf(SMALL_DIMENSION, SMALL_DIMENSION);

        final Optional<byte[]> jpeg = processor.toCleanJpeg(small);

        assertThat(jpeg).isPresent();
        final BufferedImage decoded = read(jpeg.orElseThrow());
        assertThat(decoded.getWidth()).isEqualTo(SMALL_DIMENSION);
    }

    @Test
    @DisplayName("bytes that do not decode as an image are rejected")
    void rejectsNonImage() {
        final byte[] notAnImage = "<html>this is not an image</html>".getBytes(StandardCharsets.UTF_8);

        final Optional<byte[]> jpeg = processor.toCleanJpeg(notAnImage);

        assertThat(jpeg).isEmpty();
    }

    private static byte[] pngOf(final int width, final int height) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }

    private static BufferedImage read(final byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
