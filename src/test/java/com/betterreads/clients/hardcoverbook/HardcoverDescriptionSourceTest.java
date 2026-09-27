package com.betterreads.clients.hardcoverbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.DescriptionLookup;
import com.betterreads.booksource.SourceBook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

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
        when(client.fetchByHardcoverId(any())).thenReturn(Optional.of(bookWith(BLURB)));

        final Optional<String> description = source.fetch(lookupWith(hardcoverId));

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
