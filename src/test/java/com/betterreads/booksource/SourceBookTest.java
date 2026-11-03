package com.betterreads.booksource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceBookTest {

    @Test
    void shouldKeepTheCompilationMarkInACopy() {
        final SourceBook book = SourceBook.builder(BookFieldSource.HARDCOVER).proseCompilation(true).build();

        final SourceBook copy = book.toBuilder().build();

        assertThat(copy.proseCompilation()).isTrue();
    }
}
