package com.betterreads.book;

import static com.betterreads.book.BookSeriesSamples.COSMERE;
import static com.betterreads.book.BookSeriesSamples.STORMLIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.EntityManager;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.MergedBook;
import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class BookUpsertServiceImplTest {

    private static final long BOOK_ID = 7L;

    private static final String WORK_KEY = "OL1W";

    private static final String TITLE = "Edgedancer";

    private final BookRepository books = Mockito.mock(BookRepository.class);

    private final BookUpsertServiceImpl service = new BookUpsertServiceImpl(
        books,
        Mockito.mock(AuthorResolver.class),
        Mockito.mock(SeriesChangeRecorder.class),
        Mockito.mock(MetadataChangeRecorder.class),
        Mockito.mock(EntityManager.class),
        Mockito.mock(ApplicationEventPublisher.class));

    @BeforeEach
    void setUp() {
        when(books.save(any(Book.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void shouldKeepSeriesWhenTheSourceHasNoHardcoverId() {
        final Book stored = stored(null);

        service.upsertFromSource(source(BookFieldSource.OPEN_LIBRARY, null));

        assertThat(stored.getSeries()).containsExactly(STORMLIGHT);
    }

    @Test
    void shouldKeepSeriesWhenHardcoverReturnsAnotherBook() {
        final Book stored = stored("hc-1");
        final MergedBook merged = new MergedBook(source(BookFieldSource.HARDCOVER, "hc-2"),
            Map.of(), Set.of(), Set.of(BookFieldSource.HARDCOVER));

        service.upsertFromSource(merged);

        assertThat(stored.getSeries()).containsExactly(STORMLIGHT);
    }

    private Book stored(final @Nullable String hardcoverId) {
        final Book book = new Book();
        book.applyFrom(source(BookFieldSource.OPEN_LIBRARY, hardcoverId));
        book.applySeries(List.of(STORMLIGHT), true);
        book.setBookId(BOOK_ID);
        when(books.findByOpenLibraryWorkKey(WORK_KEY)).thenReturn(Optional.of(book));
        return book;
    }

    private static SourceBook source(final BookFieldSource origin, final @Nullable String hardcoverId) {
        return SourceBook.builder(origin)
            .openLibraryWorkKey(WORK_KEY)
            .hardcoverId(hardcoverId)
            .title(TITLE)
            .series(List.of(COSMERE))
            .build();
    }
}
