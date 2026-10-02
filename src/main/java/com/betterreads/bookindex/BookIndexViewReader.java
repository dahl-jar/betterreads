package com.betterreads.bookindex;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.betterreads.book.Author;
import com.betterreads.book.Book;
import com.betterreads.book.BookRepository;
import com.betterreads.book.BookSubject;
import com.betterreads.images.CoverImages;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookIndexViewReader {

    private final BookRepository books;

    private final CoverImages coverImages;

    public BookIndexViewReader(final BookRepository books, final CoverImages coverImages) {
        this.books = books;
        this.coverImages = coverImages;
    }

    @Transactional(readOnly = true)
    public Optional<BookIndexView> indexViewByKey(final String dedupKey) {
        return books.findByDedupKey(dedupKey).map(this::toIndexView);
    }

    @Transactional(readOnly = true)
    public List<Long> idsChangedSince(final OffsetDateTime since, final long afterId, final int limit) {
        return books.findIdsChangedSince(since, afterId, PageRequest.ofSize(limit));
    }

    @Transactional(readOnly = true)
    public List<BookIndexView> indexViewsByIds(final List<Long> bookIds) {
        return books.findWithSubjectsByBookIdIn(bookIds).stream().map(this::toIndexView).toList();
    }

    private BookIndexView toIndexView(final Book book) {
        return new BookIndexView(
            book.getDedupKey(),
            book.getTitle(),
            book.getSubtitle(),
            book.getSeriesName(),
            book.getSeriesPosition(),
            book.getSeries(),
            Author.sortedNames(book.getAuthors()),
            book.getSubjects().stream().map(BookSubject::getSubject).toList(),
            book.getLanguage(),
            coverImages.servedUrl(book),
            book.getFirstPublishYear(),
            book.getAverageRating(),
            book.getRatingCount());
    }
}
