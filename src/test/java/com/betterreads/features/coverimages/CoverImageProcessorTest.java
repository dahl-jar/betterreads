package com.betterreads.features.coverimages;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoverImageProcessorTest {

    private static final int LARGE_WIDTH = 1600;

    private static final int LARGE_HEIGHT = 400;

    private static final int RESIZED_HEIGHT = 200;

    private static final int SMALL_DIMENSION = 200;

    private static final int OVER_CAP_WIDTH = 40_001;

    private static final int OVER_CAP_HEIGHT = 1_000;

    private static final int TRUNCATED_LENGTH = 60;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8};

    private final CoverImageProcessor processor = new CoverImageProcessor();

    @Test
    @DisplayName("an oversized image is re-encoded as jpeg with its longest side at the max dimension")
    void resizesLargeImage() {
        final byte[] large = pngOf(LARGE_WIDTH, LARGE_HEIGHT);

        final Optional<byte[]> jpeg = processor.toCleanJpeg(large);

        final byte[] bytes = jpeg.orElseThrow();
        final BufferedImage decoded = read(bytes);
        assertThat(bytes).startsWith(JPEG_MAGIC);
        assertThat(decoded.getWidth()).isEqualTo(CoverImageProcessor.MAX_DIMENSION);
        assertThat(decoded.getHeight()).isEqualTo(RESIZED_HEIGHT);
    }

    @Test
    @DisplayName("an image whose header declares more than the pixel cap is rejected")
    void shouldRejectImageOverPixelCap() {
        final byte[] huge = binaryPngOf(OVER_CAP_WIDTH, OVER_CAP_HEIGHT);

        final Optional<byte[]> jpeg = processor.toCleanJpeg(huge);

        assertThat(jpeg).isEmpty();
    }

    @Test
    @DisplayName("an image cut off after its header is rejected")
    void shouldRejectTruncatedImage() {
        final byte[] truncated = Arrays.copyOf(pngOf(SMALL_DIMENSION, SMALL_DIMENSION), TRUNCATED_LENGTH);

        final Optional<byte[]> jpeg = processor.toCleanJpeg(truncated);

        assertThat(jpeg).isEmpty();
    }

    @Test
    @DisplayName("a small image keeps its size")
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

    static byte[] pngOf(final int width, final int height) {
        return png(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
    }

    private static byte[] binaryPngOf(final int width, final int height) {
        return png(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY));
    }

    private static byte[] png(final BufferedImage image) {
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
