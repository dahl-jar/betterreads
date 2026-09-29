package com.betterreads.features.metadatacheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.betterreads.book.VerifiedMetadata;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StoredNamesTest {

    private static final String RED_RISING = "Red Rising";

    private static final String BROWN = "Pierce Brown";

    private static final String SHOUTED_NAME = "MARYANN EVANS";

    private static final String SAGA = "The Red Rising Saga";

    private static VerifiedMetadata authors(final String... names) {
        return new VerifiedMetadata(null, List.of(names), null, null, null, null, null);
    }

    private static VerifiedMetadata series(final String name) {
        return new VerifiedMetadata(null, null, null, name, 1, null, null);
    }

    private static @Nullable String title(final String stored, final String confirmed) {
        final VerifiedMetadata metadata = new VerifiedMetadata(confirmed, null, null, null, null, null, null);
        return new StoredNames(List.of(), List.of()).withStoredSpelling(metadata, stored).title();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Red Rising | Red Rising: Book One of the Red Rising Saga | Red Rising",
        "Red Rising | Red Rising (Red Rising Saga #1) | Red Rising",
        "Red Rising | Red Rising - A Novel | Red Rising",
        "Red Rising | RED-RISING | Red Rising",
        "Red Rising: Book One | Red Rising | Red Rising: Book One",
        "Red | Red Rising | Red Rising",
        "Mistborn: The Final Empire | Mistborn: The Well of Ascension | Mistborn: The Well of Ascension"})
    void shouldKeepStoredTitleOnlyForTheSameBook(final String stored, final String confirmed, final String expected) {
        final String result = title(stored, confirmed);

        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Pierce Brown | BROWN, Pierce",
        "Martin Luther King Jr. | Martin Luther King, Jr.",
        "Gabriel García Márquez | gabriel garcía márquez"})
    void shouldUseStoredAuthor(final String stored, final String confirmed) {
        final StoredNames names = new StoredNames(List.of(), List.of(stored));

        final VerifiedMetadata result = names.withStoredSpelling(authors(confirmed), RED_RISING);

        assertThat(result.authors()).containsExactly(stored);
    }

    @Test
    void shouldKeepConfirmedAuthorWhenStoredNamesClash() {
        final StoredNames names = new StoredNames(List.of(), List.of("Mary Ann Evans", "Maryann Evans"));

        final VerifiedMetadata result = names.withStoredSpelling(authors(SHOUTED_NAME), RED_RISING);

        assertThat(result.authors()).containsExactly(SHOUTED_NAME);
    }

    @Test
    void shouldIgnoreNamesWithoutLetters() {
        final StoredNames names = new StoredNames(List.of(), List.of("...", BROWN));

        final VerifiedMetadata result = names.withStoredSpelling(authors("-"), RED_RISING);

        assertThat(result.authors()).containsExactly("-");
    }

    @Test
    void shouldUseStoredSeries() {
        final StoredNames names = new StoredNames(List.of(SAGA), List.of());

        final VerifiedMetadata result = names.withStoredSpelling(series("red rising saga"), RED_RISING);

        assertThat(result.seriesName()).isEqualTo(SAGA);
    }
}
