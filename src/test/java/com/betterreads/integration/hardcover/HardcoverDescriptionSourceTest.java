package com.betterreads.integration.hardcover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.DescriptionLookup;
import com.betterreads.catalog.service.source.SourceBook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link HardcoverDescriptionSource}. The source resolves a description through the
 * book record the lookup's Hardcover id identifies, and resolves empty without one.
 */
class HardcoverDescriptionSourceTest {

    private static final String HARDCOVER_ID = "379004";

    private static final String TITLE = "Empire of Silence";

    private static final String BLURB =
        "Hadrian Marlowe, a man revered as a hero and despised as a murderer, recounts his tale of "
        + "the war that crushed an alien empire and burned a sun to end a world.";

    private final HardcoverClient client = mock(HardcoverClient.class);

    private final HardcoverDescriptionSource source = new HardcoverDescriptionSource(client);

    @Test
    @DisplayName("returns the book record's description for the lookup's Hardcover id")
    void returnsTheBookDescription() {
        when(client.fetchByHardcoverId(HARDCOVER_ID)).thenReturn(Optional.of(bookWith(BLURB)));

        final Optional<String> description = source.fetch(lookupWith(HARDCOVER_ID));

        assertThat(description).contains(BLURB);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = " ")
    @DisplayName("resolves empty without a Hardcover id")
    void resolvesEmptyWithoutAnId(final String hardcoverId) {
        final Optional<String> description = source.fetch(lookupWith(hardcoverId));

        assertThat(description).isEmpty();
    }

    @Test
    @DisplayName("resolves empty when the book is not found")
    void resolvesEmptyWhenTheBookIsNotFound() {
        when(client.fetchByHardcoverId(HARDCOVER_ID)).thenReturn(Optional.empty());

        final Optional<String> description = source.fetch(lookupWith(HARDCOVER_ID));

        assertThat(description).isEmpty();
    }

    @Test
    @DisplayName("resolves empty when the book record carries no description")
    void resolvesEmptyWhenTheBookHasNoDescription() {
        when(client.fetchByHardcoverId(HARDCOVER_ID)).thenReturn(Optional.of(bookWith(null)));

        final Optional<String> description = source.fetch(lookupWith(HARDCOVER_ID));

        assertThat(description).isEmpty();
    }

    private static DescriptionLookup lookupWith(final String hardcoverId) {
        return new DescriptionLookup(null, null, TITLE, "Christopher Ruocchio", null, hardcoverId);
    }

    private static SourceBook bookWith(final String description) {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .title(TITLE)
            .description(description)
            .build();
    }
}
