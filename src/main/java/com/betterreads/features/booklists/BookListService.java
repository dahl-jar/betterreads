package com.betterreads.features.booklists;

import java.util.List;

import com.betterreads.book.Author;
import com.betterreads.book.Book;
import com.betterreads.book.BookOrder;
import com.betterreads.book.BookRepository;
import com.betterreads.images.CoverImages;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BookListService {

    private static final int TOP_RATED_RATING_FLOOR = 1000;

    private final BookListRepository books;

    private final BookRepository catalog;

    private final CoverImages coverImages;

    public BookListService(
        final BookListRepository books,
        final BookRepository catalog,
        final CoverImages coverImages
    ) {
        this.books = books;
        this.catalog = catalog;
        this.coverImages = coverImages;
    }

    @Transactional(readOnly = true)
    public List<BookCardResponse> list(final BookListType type, final int limit) {
        final Pageable page = PageRequest.ofSize(limit);
        final List<Long> ids = switch (type) {
            case RECENTLY_ADDED -> books.findIdsRecentlyAdded(page);
            case TOP_RATED -> books.findIdsTopRated(TOP_RATED_RATING_FLOOR, page);
        };
        return BookOrder.byIds(ids, catalog.findByBookIdIn(ids)).stream().map(this::toCard).toList();
    }

    @Transactional(readOnly = true)
    public BookCountResponse count() {
        return new BookCountResponse(books.count());
    }

    private BookCardResponse toCard(final Book book) {
        return new BookCardResponse(
            book.getDedupKey(),
            book.getTitle(),
            Author.names(book.getAuthors()),
            coverImages.servedUrl(book),
            book.getFirstPublishYear(),
            book.getAverageRating(),
            book.getRatingCount());
    }
}
