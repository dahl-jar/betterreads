package com.betterreads.bookaccess;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
    public List<BookSummary> summariesByIds(final Collection<Long> bookIds) {
        return books.findByBookIdIn(bookIds).stream().map(this::toSummary).toList();
    }

    private BookSummary toSummary(final Book book) {
        return new BookSummary(
            book.getBookId(),
            book.getDedupKey(),
            book.getTitle(),
            Author.sortedNames(book.getAuthors()),
            coverImages.servedUrl(book),
            book.getAverageRating());
    }
}
