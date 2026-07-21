package com.betterreads.catalog.service.read;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.dto.BookSummary;
import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.image.CoverImages;
import com.betterreads.catalog.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads books as listing summaries. */
@Service
public class BookSummaryReader {

    private final BookRepository books;

    private final CoverImages coverImages;

    public BookSummaryReader(final BookRepository books, final CoverImages coverImages) {
        this.books = books;
        this.coverImages = coverImages;
    }

    /** Returns the summary for the book with the given key, or empty when none matches. */
    @Transactional(readOnly = true)
    public Optional<BookSummary> summaryByKey(final String dedupKey) {
        return books.findByDedupKey(dedupKey).map(this::toSummary);
    }

    /** Returns the summaries for the given book ids, authors fetched in one query. */
    @Transactional(readOnly = true)
    public List<BookSummary> summariesByIds(final Collection<Long> bookIds) {
        return books.findByBookIdIn(bookIds).stream().map(this::toSummary).toList();
    }

    private BookSummary toSummary(final Book book) {
        return new BookSummary(
            book.getBookId(),
            book.getDedupKey(),
            book.getTitle(),
            book.getAuthors().stream().map(Author::getName).sorted().toList(),
            coverImages.servedUrl(book.getDedupKey(), book.getCoverUrl()),
            book.getAverageRating());
    }
}
