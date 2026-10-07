package com.betterreads.features.search;

import com.betterreads.isbn.Isbn13;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CatalogFallback {

    private static final Pageable LIMIT = PageRequest.ofSize(20);

    private static final Pattern SEPARATORS = Pattern.compile("[\\s-]");

    private final BookSearchRepository books;

    public List<Long> find(final String query) {
        final String compact = SEPARATORS.matcher(query).replaceAll("").toUpperCase(Locale.ROOT);
        final String isbn = Isbn13.isValid(compact) ? compact : Isbn13.fromIsbn10(compact);
        if (isbn != null) {
            return books.findIdsByIsbn(isbn, LIMIT);
        }
        return books.findIdsByTitleIgnoreCase(query.strip(), LIMIT);
    }
}
