package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.blank;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
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
        final byte[] large = blank(LARGE_WIDTH, LARGE_HEIGHT);

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
        final byte[] truncated = Arrays.copyOf(blank(SMALL_DIMENSION, SMALL_DIMENSION), TRUNCATED_LENGTH);

        final Optional<byte[]> jpeg = processor.toCleanJpeg(truncated);

        assertThat(jpeg).isEmpty();
    }

    @Test
    @DisplayName("a small image keeps its size")
    void keepsSmallImage() {
        final byte[] small = blank(SMALL_DIMENSION, SMALL_DIMENSION);

        final Optional<byte[]> jpeg = processor.toCleanJpeg(small);

        assertThat(jpeg).isPresent();
        final BufferedImage decoded = read(jpeg.orElseThrow());
        assertThat(decoded.getWidth()).isEqualTo(SMALL_DIMENSION);
    }

    @Test
    @DisplayName("bytes that do not decode as an image are rejected")
    void rejectsNonImage() {
        final byte[] notAnImage = CoverImageFixtures.notImage();

        final Optional<byte[]> jpeg = processor.toCleanJpeg(notAnImage);

        assertThat(jpeg).isEmpty();
    }

    private static byte[] binaryPngOf(final int width, final int height) {
        return CoverImageFixtures.png(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY));
    }

    private static BufferedImage read(final byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
