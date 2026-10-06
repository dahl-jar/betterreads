package com.betterreads.clients.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SourceGroupTest {

    @Test
    void shouldRouteAGraphicNovelToComics() {
        final SourceGroup group = SourceGroup.of(List.of("fiction", "graphic novel"));

        assertThat(group).isEqualTo(SourceGroup.COMIC);
    }

    @Test
    void shouldPreferComicsOverFantasy() {
        final SourceGroup group = SourceGroup.of(List.of("fantasy", "comics"));

        assertThat(group).isEqualTo(SourceGroup.COMIC);
    }

    @Test
    void shouldFallBackToGeneral() {
        final SourceGroup group = SourceGroup.of(List.of("mystery"));

        assertThat(group).isEqualTo(SourceGroup.GENERAL);
    }
}
