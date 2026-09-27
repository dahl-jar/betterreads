package com.betterreads.clients.hardcoverauthor;

import java.util.List;
import java.util.Optional;

import com.betterreads.booksource.SourceAuthorWorks;
import com.betterreads.booksource.SourceBook;
import com.betterreads.clients.hardcover.HardcoverBookNodeMapper;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Maps a Hardcover author's contributions to their works. */
@Component
class HardcoverAuthorMapper {

    private static final int MAX_BOOKS = 50;

    /** Returns the author's works, or null when the name or every book is missing. */
    public @Nullable SourceAuthorWorks toSourceAuthorWorks(
        final @Nullable String authorName,
        final AuthorWorksResponse.Author author
    ) {
        if (authorName == null || author.contributions() == null) {
            return null;
        }
        final List<SourceBook> books = author.contributions().stream()
            .map(AuthorWorksResponse.Contribution::book)
            .map(HardcoverBookNodeMapper::toSourceBookWithSeries)
            .flatMap(Optional::stream)
            .limit(MAX_BOOKS)
            .toList();
        return books.isEmpty() ? null : new SourceAuthorWorks(authorName, books);
    }
}
