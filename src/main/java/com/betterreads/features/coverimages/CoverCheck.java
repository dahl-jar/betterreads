package com.betterreads.features.coverimages;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

@Component
class CoverCheck {

    private static final int MIN_WIDTH = 300;

    private static final double MIN_ASPECT = 1.33;

    private static final double MAX_ASPECT = 1.82;

    private static final double MIN_LUMINANCE_SPREAD = 3.0;

    private static final int SAMPLE_WIDTH = 32;

    private static final int SAMPLE_HEIGHT = 48;

    private static final int RED_SHIFT = 16;

    private static final int GREEN_SHIFT = 8;

    private static final int CHANNEL_MASK = 0xFF;

    private static final double RED_WEIGHT = 0.299;

    private static final double GREEN_WEIGHT = 0.587;

    private static final double BLUE_WEIGHT = 0.114;

    boolean passes(final byte[] image) {
        return ImageDecoder.decode(image)
            .filter(decoded -> decoded.getWidth() >= MIN_WIDTH && looksLikeCover(decoded))
            .isPresent();
    }

    boolean isWrongImage(final byte[] image) {
        return ImageDecoder.decode(image)
            .map(decoded -> !looksLikeCover(decoded))
            .orElse(true);
    }

    private static boolean looksLikeCover(final BufferedImage decoded) {
        final double aspect = (double) decoded.getHeight() / decoded.getWidth();
        return aspect >= MIN_ASPECT && aspect <= MAX_ASPECT && varied(decoded);
    }

    private static boolean varied(final BufferedImage decoded) {
        final double[] luminance = IntStream.range(0, SAMPLE_WIDTH * SAMPLE_HEIGHT)
            .map(index -> decoded.getRGB(
                index % SAMPLE_WIDTH * decoded.getWidth() / SAMPLE_WIDTH,
                index / SAMPLE_WIDTH * decoded.getHeight() / SAMPLE_HEIGHT))
            .mapToDouble(CoverCheck::luminance)
            .toArray();
        final double mean = Arrays.stream(luminance).average().orElse(0);
        final double variance = Arrays.stream(luminance).map(value -> (value - mean) * (value - mean))
            .average().orElse(0);
        return Math.sqrt(variance) >= MIN_LUMINANCE_SPREAD;
    }

    private static double luminance(final int rgb) {
        return RED_WEIGHT * (rgb >> RED_SHIFT & CHANNEL_MASK) + GREEN_WEIGHT * (rgb >> GREEN_SHIFT & CHANNEL_MASK)
            + BLUE_WEIGHT * (rgb & CHANNEL_MASK);
    }
}
