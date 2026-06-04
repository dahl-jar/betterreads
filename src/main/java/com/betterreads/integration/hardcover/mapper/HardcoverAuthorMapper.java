package com.betterreads.integration.hardcover.mapper;

import java.util.List;
import java.util.Optional;

import com.betterreads.catalog.service.source.SourceAuthorWorks;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.hardcover.dto.AuthorWorksResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link SourceAuthorWorks} from a Hardcover author name and their contributions.
 *
 * <p>The contributions arrive ordered by readers. A book survives when it is the canonical work, has
 * an English edition, and its title is a single book rather than a boxed set. Each surviving book
 * carries the fields the enumeration returned, so the seed needs no further Hardcover call. Order is
 * preserved and the list is capped at {@link #MAX_BOOKS}.
 */
@Component
public class HardcoverAuthorMapper {

    static final int MAX_BOOKS = 50;

    /** Returns the author's works, or null when the name or every book is missing. */
    public @Nullable SourceAuthorWorks toSourceAuthorWorks(
        final @Nullable String authorName,
        final AuthorWorksResponse.@Nullable Author author
    ) {
        if (authorName == null || author == null || author.contributions() == null) {
            return null;
        }
        final List<SourceBook> books = author.contributions().stream()
            .map(AuthorWorksResponse.Contribution::book)
            .map(HardcoverBookNodeMapper::toSourceBookWithSeries)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .limit(MAX_BOOKS)
            .toList();
        return books.isEmpty() ? null : new SourceAuthorWorks(authorName, books);
    }
}
