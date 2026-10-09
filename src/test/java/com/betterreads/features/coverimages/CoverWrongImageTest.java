package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.patterned;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoverWrongImageTest {

    private final CoverCheck check = new CoverCheck();

    @Test
    void shouldKeepASmallCoverShapedImage() {
        final byte[] thumbnail = patterned(128, 192);

        final boolean wrong = check.isWrongImage(thumbnail);

        assertThat(wrong).isFalse();
    }

    @Test
    void shouldCallALandscapeImageWrong() {
        final byte[] photo = patterned(200, 150);

        final boolean wrong = check.isWrongImage(photo);

        assertThat(wrong).isTrue();
    }

    @Test
    void shouldCallBytesThatAreNotAnImageWrong() {
        final byte[] notImage = CoverImageFixtures.notImage();

        final boolean wrong = check.isWrongImage(notImage);

        assertThat(wrong).isTrue();
    }
}
