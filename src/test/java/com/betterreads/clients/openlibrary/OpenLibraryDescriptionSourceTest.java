package com.betterreads.clients.openlibrary;

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

class OpenLibraryDescriptionSourceTest {

    private static final String WORK_KEY = "OL17081443W";

    private static final String TITLE = "Red Rising";

    private static final String BLURB =
        "Darrow is a Red, a miner in the interior of Mars, who digs to make the surface livable "
        + "for future generations until he learns the surface has been terraformed for decades.";

    private final OpenLibraryClient client = mock(OpenLibraryClient.class);

    private final OpenLibraryDescriptionSource source = new OpenLibraryDescriptionSource(client);

    @Test
    @DisplayName("returns the work record's description for the lookup's work key")
    void returnsTheWorkDescription() {
        when(client.fetchByWorkKey(WORK_KEY)).thenReturn(Optional.of(bookWith(BLURB)));

        final Optional<String> description = source.fetch(lookupWith(WORK_KEY));

        assertThat(description).contains(BLURB);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = " ")
    @DisplayName("resolves empty without a work key")
    void resolvesEmptyWithoutAWorkKey(final String workKey) {
        when(client.fetchByWorkKey(any())).thenReturn(Optional.of(bookWith(BLURB)));

        final Optional<String> description = source.fetch(lookupWith(workKey));

        assertThat(description).isEmpty();
    }

    private static DescriptionLookup lookupWith(final String workKey) {
        return new DescriptionLookup(null, null, TITLE, "Pierce Brown", workKey, null);
    }

    private static SourceBook bookWith(final String description) {
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .title(TITLE)
            .description(description)
            .build();
    }
}
