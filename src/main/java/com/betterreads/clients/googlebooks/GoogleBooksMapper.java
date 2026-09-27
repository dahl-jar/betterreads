package com.betterreads.clients.googlebooks;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.betterreads.bookdescription.DescriptionCleaner;
import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.CatalogGenres;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Maps Google Books volumes to {@code SourceBook}. */
@Component
class GoogleBooksMapper {

    private static final Pattern YEAR_PREFIX = Pattern.compile("^(\\d{4})");

    private static final String ISBN_13_TYPE = "ISBN_13";

    private static final String HTTP_PREFIX = "http://";

    private static final String HTTPS_PREFIX = "https://";

    @Nullable SourceBook toSourceBook(final @Nullable GoogleBooksVolume volume) {
        if (volume == null || volume.volumeInfo() == null) {
            return null;
        }
        final GoogleBooksVolumeInfo info = volume.volumeInfo();
        return SourceBook.builder(BookFieldSource.GOOGLE_BOOKS)
            .isbn13(findIsbn13(info.industryIdentifiers()))
            .googleBooksVolumeId(volume.id())
            .title(info.title())
            .subtitle(info.subtitle())
            .description(stripHtml(info.description()))
            .publicationYear(parseYear(info.publishedDate()))
            .publisher(info.publisher())
            .pageCount(nullIfZero(info.pageCount()))
            .language(info.language())
            .coverUrl(coverUrl(info.imageLinks()))
            .authors(SourceAuthor.ofNames(info.authors()))
            .rawSubjects(info.categories() == null
                ? null : CatalogGenres.reduceToCanonical(info.categories()))
            .build();
    }

    /**
     * Google serves thumbnails over http, upgraded to https so the cover loads on an https page
     * without a mixed-content block.
     */
    static @Nullable String coverUrl(final @Nullable ImageLinks imageLinks) {
        if (imageLinks == null) {
            return null;
        }
        final String thumbnail = imageLinks.thumbnail() == null
            ? imageLinks.smallThumbnail() : imageLinks.thumbnail();
        if (thumbnail == null || thumbnail.isBlank()) {
            return null;
        }
        return thumbnail.startsWith(HTTP_PREFIX)
            ? HTTPS_PREFIX + thumbnail.substring(HTTP_PREFIX.length())
            : thumbnail;
    }

    static @Nullable String findIsbn13(final @Nullable List<IndustryIdentifier> identifiers) {
        if (identifiers == null) {
            return null;
        }
        return identifiers.stream()
            .filter(id -> ISBN_13_TYPE.equals(id.type()))
            .map(IndustryIdentifier::identifier)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
    }

    static @Nullable Integer parseYear(final @Nullable String publishedDate) {
        if (publishedDate == null) {
            return null;
        }
        final Matcher matcher = YEAR_PREFIX.matcher(publishedDate);
        if (!matcher.find()) {
            return null;
        }
        return Integer.valueOf(matcher.group(1));
    }

    /** Google sends a page count of 0 on reprints that have no real one. */
    static @Nullable Integer nullIfZero(final @Nullable Integer pageCount) {
        if (pageCount == null || pageCount == 0) {
            return null;
        }
        return pageCount;
    }

    /** Descriptions arrive with {@code <p>}, {@code <b>}, {@code <i>} and {@code <br>} markup. */
    static @Nullable String stripHtml(final @Nullable String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return DescriptionCleaner.stripHtml(text);
    }
}
