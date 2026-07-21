package com.betterreads.catalog.service.read;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.dto.BookIndexView;
import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.entity.BookSubject;
import com.betterreads.catalog.image.CoverImages;
import com.betterreads.catalog.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads books as search index views. */
@Service
public class BookIndexViewReader {

    private final BookRepository books;

    private final CoverImages coverImages;

    public BookIndexViewReader(final BookRepository books, final CoverImages coverImages) {
        this.books = books;
        this.coverImages = coverImages;
    }

    /** Returns the index view for the book with the given key, or empty when none matches. */
    @Transactional(readOnly = true)
    public Optional<BookIndexView> indexViewByKey(final String dedupKey) {
        return books.findByDedupKey(dedupKey).map(this::toIndexView);
    }

    /** Returns the index view for every catalog book, authors and subjects fetched in one query. */
    @Transactional(readOnly = true)
    public List<BookIndexView> allForIndex() {
        return books.findAllBy().stream().map(this::toIndexView).toList();
    }

    private BookIndexView toIndexView(final Book book) {
        return new BookIndexView(
            book.getDedupKey(),
            book.getTitle(),
            book.getSubtitle(),
            book.getSeriesName(),
            book.getSeriesPosition(),
            book.getAuthors().stream().map(Author::getName).sorted().toList(),
            book.getSubjects().stream().map(BookSubject::getSubject).toList(),
            book.getLanguage(),
            coverImages.servedUrl(book.getDedupKey(), book.getCoverUrl()),
            book.getFirstPublishYear(),
            book.getAverageRating(),
            book.getRatingCount());
    }
}
