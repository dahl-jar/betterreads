package com.betterreads.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BookOrderTest {

    private static final long FIRST_ID = 1L;

    private static final long SECOND_ID = 2L;

    private static final long THIRD_ID = 3L;

    private static final List<Long> IDS = List.of(THIRD_ID, FIRST_ID, SECOND_ID);

    @Test
    void shouldFollowIdOrder() {
        final Book first = book(FIRST_ID);
        final Book second = book(SECOND_ID);
        final Book third = book(THIRD_ID);

        final List<Book> ordered = BookOrder.byIds(IDS, List.of(first, second, third));

        assertThat(ordered).containsExactly(third, first, second);
    }

    @Test
    void shouldDropIdWithoutBook() {
        final Book first = book(FIRST_ID);
        final Book second = book(SECOND_ID);

        final List<Book> ordered = BookOrder.byIds(IDS, List.of(first, second));

        assertThat(ordered).containsExactly(first, second);
    }

    private static Book book(final long bookId) {
        final Book book = new Book();
        book.setBookId(bookId);
        return book;
    }
}
