package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.gray;
import static com.betterreads.features.coverimages.CoverImageFixtures.patterned;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoverCheckTest {

    private static final int FAINT_BASE = 128;

    private static final int FAINT_STEP = 2;

    private final CoverCheck check = new CoverCheck();

    @Test
    void shouldPassACoverAtTheMinimumWidth() {
        final byte[] image = patterned(300, 450);

        final boolean passes = check.passes(image);

        assertThat(passes).isTrue();
    }

    @Test
    void shouldDropAnImageBelowTheMinimumWidth() {
        final byte[] image = patterned(299, 450);

        final boolean passes = check.passes(image);

        assertThat(passes).isFalse();
    }

    @Test
    void shouldPassACoverAtTheWidestShape() {
        final byte[] image = patterned(300, 399);

        final boolean passes = check.passes(image);

        assertThat(passes).isTrue();
    }

    @Test
    void shouldDropAnImageWiderThanACover() {
        final byte[] image = patterned(300, 398);

        final boolean passes = check.passes(image);

        assertThat(passes).isFalse();
    }

    @Test
    void shouldPassACoverAtTheTallestShape() {
        final byte[] image = patterned(400, 728);

        final boolean passes = check.passes(image);

        assertThat(passes).isTrue();
    }

    @Test
    void shouldDropAnImageTallerThanACover() {
        final byte[] image = patterned(400, 729);

        final boolean passes = check.passes(image);

        assertThat(passes).isFalse();
    }

    @Test
    void shouldDropANearlyUniformImage() {
        final byte[] image = gray(600, 900, (x, y) -> FAINT_BASE + (x + y) % 2 * FAINT_STEP);

        final boolean passes = check.passes(image);

        assertThat(passes).isFalse();
    }

    @Test
    void shouldDropBytesThatAreNotAnImage() {
        final byte[] notImage = CoverImageFixtures.notImage();

        final boolean passes = check.passes(notImage);

        assertThat(passes).isFalse();
    }
}
