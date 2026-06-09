package com.betterreads.catalog.service.read;

import java.util.List;

import com.betterreads.catalog.dto.BookCardResponse;
import com.betterreads.catalog.entity.Author;
import com.betterreads.catalog.entity.Book;
import com.betterreads.catalog.image.CoverImages;
import com.betterreads.catalog.repository.BookListRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves the homepage book lists from the catalog. Only promoted, complete books live in
 * {@code book}, so a list never needs a completeness filter.
 */
@Service
public class BookListService {

    private static final int MIN_RATINGS_FOR_TOP_RATED = 1000;

    private final BookListRepository books;

    private final CoverImages coverImages;

    public BookListService(final BookListRepository books, final CoverImages coverImages) {
        this.books = books;
        this.coverImages = coverImages;
    }

    /** Returns the cards for the given list, capped at {@code limit}. */
    @Transactional(readOnly = true)
    public List<BookCardResponse> list(final BookListType type, final int limit) {
        final Pageable page = PageRequest.ofSize(limit);
        final List<Book> found = switch (type) {
            case RECENTLY_ADDED -> books.findRecentlyAdded(page);
            case TOP_RATED -> books.findTopRated(MIN_RATINGS_FOR_TOP_RATED, page);
        };
        return found.stream().map(this::toCard).toList();
    }

    private BookCardResponse toCard(final Book book) {
        return new BookCardResponse(
            book.getDedupKey(),
            book.getTitle(),
            book.getAuthors().stream().map(Author::getName).sorted().toList(),
            coverImages.servedUrl(book.getDedupKey(), book.getCoverUrl()),
            book.getFirstPublishYear(),
            book.getAverageRating(),
            book.getRatingCount());
    }
}
