package com.betterreads.bookaccess;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.betterreads.book.Author;
import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.images.CoverImages;
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

    @Transactional(readOnly = true)
    public Optional<BookSummary> summaryByKey(final String dedupKey) {
        return books.findByDedupKey(dedupKey).map(this::toSummary);
    }

    /** Authors load in the same query as the books. */
    @Transactional(readOnly = true)
    public Map<Long, BookSummary> summariesKeyedById(final Collection<Long> bookIds) {
        return books.findByBookIdIn(bookIds).stream()
            .map(this::toSummary)
            .collect(Collectors.toMap(BookSummary::bookId, Function.identity()));
    }

    private BookSummary toSummary(final Book book) {
        return new BookSummary(
            book.getBookId(),
            book.getDedupKey(),
            book.getTitle(),
            Author.names(book.getAuthors()),
            coverImages.servedUrl(book),
            book.getAverageRating());
    }
}
