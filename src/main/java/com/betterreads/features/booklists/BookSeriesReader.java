package com.betterreads.features.booklists;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.betterreads.book.Book;
import com.betterreads.bookaccess.BookSummary;
import com.betterreads.bookaccess.BookSummaryReader;
import com.betterreads.errors.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BookSeriesReader {

    private static final int MAX_SERIES_BOOKS = 50;

    private final BookListRepository books;

    private final BookSummaryReader bookSummaries;

    BookSeriesReader(final BookListRepository books, final BookSummaryReader bookSummaries) {
        this.books = books;
        this.bookSummaries = bookSummaries;
    }

    @Transactional(readOnly = true)
    public List<BookSeriesResponse> seriesOf(final String key) {
        final Book book = books.findWithSeriesByDedupKey(key)
            .orElseThrow(() -> new ResourceNotFoundException("No book with key " + key));
        return book.getSeries().stream()
            .map(entry -> new BookSeriesResponse(
                entry.name(), entry.position(), othersIn(entry.name(), book.getBookId())))
            .toList();
    }

    private List<SeriesBookResponse> othersIn(final String seriesName, final Long bookId) {
        final List<SeriesPosition> others =
            books.findOthersInSeries(seriesName, bookId, PageRequest.ofSize(MAX_SERIES_BOOKS));
        final Map<Long, BookSummary> summaries = bookSummaries.summariesKeyedById(
            others.stream().map(SeriesPosition::bookId).toList());
        return others.stream().map(other -> toSeriesBook(other, summaries)).toList();
    }

    private static SeriesBookResponse toSeriesBook(
        final SeriesPosition other, final Map<Long, BookSummary> summaries) {
        final BookSummary book = Optional.ofNullable(summaries.get(other.bookId()))
            .orElseThrow(() -> new IllegalStateException(
                "series row references a missing book bookId=" + other.bookId()));
        return new SeriesBookResponse(
            book.dedupKey(), book.title(), book.authors(), book.servedCoverUrl(), other.position());
    }
}
