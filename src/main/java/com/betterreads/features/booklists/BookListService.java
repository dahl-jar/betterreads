package com.betterreads.features.booklists;

import java.util.List;

import com.betterreads.book.Author;
import com.betterreads.book.Book;
import com.betterreads.images.CoverImages;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serves the homepage book lists from the catalog. */
@Service
class BookListService {

    private static final int TOP_RATED_RATING_FLOOR = 1000;

    private final BookListRepository books;

    private final CoverImages coverImages;

    public BookListService(final BookListRepository books, final CoverImages coverImages) {
        this.books = books;
        this.coverImages = coverImages;
    }

    @Transactional(readOnly = true)
    public List<BookCardResponse> list(final BookListType type, final int limit) {
        final Pageable page = PageRequest.ofSize(limit);
        final List<Book> found = switch (type) {
            case RECENTLY_ADDED -> books.findRecentlyAdded(page);
            case TOP_RATED -> books.findTopRated(TOP_RATED_RATING_FLOOR, page);
        };
        return found.stream().map(this::toCard).toList();
    }

    @Transactional(readOnly = true)
    public BookCountResponse count() {
        return new BookCountResponse(books.count());
    }

    private BookCardResponse toCard(final Book book) {
        return new BookCardResponse(
            book.getDedupKey(),
            book.getTitle(),
            Author.sortedNames(book.getAuthors()),
            coverImages.servedUrl(book),
            book.getFirstPublishYear(),
            book.getAverageRating(),
            book.getRatingCount());
    }
}
