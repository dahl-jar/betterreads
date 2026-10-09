package com.betterreads.features.coverimages;

import java.awt.image.BufferedImage;
import java.awt.image.SampleModel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ImageDecoder {

    private static final long MAX_PIXELS = 40_000_000L;

    private static final long MAX_DECODED_BYTES = 64L * 1024 * 1024;

    private static final int MAX_PROGRESSIVE_PASSES = 32;

    private static final int BITS_PER_BYTE = 8;

    private static final long RESIZE_COPY_BYTES_PER_PIXEL = 4;

    private static final Set<String> FORMATS = Set.of("jpeg", "png", "gif", "bmp");

    private static final Logger LOG = LoggerFactory.getLogger(ImageDecoder.class);

    private ImageDecoder() {
    }

    static Optional<BufferedImage> decode(final byte[] raw) {
        return decode(raw, MAX_DECODED_BYTES, MAX_PROGRESSIVE_PASSES);
    }

    // Checkstyle.IllegalCatch + PMD.AvoidCatchingGenericException: image readers throw arbitrary unchecked errors
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    static Optional<BufferedImage> decode(final byte[] raw, final long maxDecodedBytes, final int maxPasses) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            if (input == null) {
                return Optional.empty();
            }
            return decodeWithinCap(input, maxDecodedBytes, maxPasses);
        } catch (IOException | RuntimeException ex) {
            LOG.debug("catalog.cover-decode could not decode cover ({})", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Optional<BufferedImage> decodeWithinCap(
        final ImageInputStream input, final long maxDecodedBytes, final int maxPasses
    ) throws IOException {
        final Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            return Optional.empty();
        }
        final ImageReader reader = readers.next();
        try {
            if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
            reader.setInput(input, true, true);
            final long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
            final long bytes = pixels * (bytesPerPixel(reader) + RESIZE_COPY_BYTES_PER_PIXEL);
            if (pixels > MAX_PIXELS || bytes > maxDecodedBytes) {
                return Optional.empty();
            }
            final ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceProgressivePasses(0, maxPasses);
            return Optional.of(reader.read(0, param));
        } finally {
            reader.dispose();
        }
    }

    private static long bytesPerPixel(final ImageReader reader) throws IOException {
        final SampleModel samples = reader.getImageTypes(0).next().getSampleModel();
        final int bits = Arrays.stream(samples.getSampleSize()).sum();
        return (bits + BITS_PER_BYTE - 1L) / BITS_PER_BYTE;
    }
}
