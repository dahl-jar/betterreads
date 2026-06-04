package com.betterreads.integration.googlebooks.mapper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.CatalogGenres;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.googlebooks.dto.IndustryIdentifier;
import com.betterreads.integration.googlebooks.dto.Volume;
import com.betterreads.integration.googlebooks.dto.VolumeInfo;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Translates Google Books DTOs to the {@link SourceBook} contract used by the catalog layer.
 *
 * <p>{@code pageCount} is {@code 0} on reprints where Google has metadata but no real page
 * count, so it is nulled. {@code industryIdentifiers} sometimes carries only an {@code ISBN_10},
 * never synthesized into an ISBN-13. {@code description} ships with embedded HTML
 * ({@code <p>}, {@code <b>}, {@code <i>}, {@code <br>}), stripped before persistence so the
 * tags do not leak into rendered catalog text. {@code categories} are reduced to canonical genres
 * like the other sources. Rating is not mapped; only Hardcover supplies a trustworthy rating.
 */
@Component
public class GoogleBooksMapper {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private static final Pattern YEAR_PREFIX = Pattern.compile("^(\\d{4})");

    private static final String ISBN_13_TYPE = "ISBN_13";

    private static final int MAX_SUBJECTS = 25;

    /** Returns the {@link SourceBook} projection of a Google Books volume, or null if unmappable. */
    public @Nullable SourceBook toSourceBook(final @Nullable Volume volume) {
        if (volume == null || volume.volumeInfo() == null) {
            return null;
        }
        final VolumeInfo info = volume.volumeInfo();
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
            .authors(SourceAuthor.ofNames(info.authors()))
            .rawSubjects(info.categories() == null ? null : canonicalSubjects(info.categories()))
            .build();
    }

    static List<String> canonicalSubjects(final @Nullable List<String> categories) {
        return CatalogGenres.reduceToCanonical(categories, MAX_SUBJECTS);
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
        if (publishedDate == null || publishedDate.isBlank()) {
            return null;
        }
        final Matcher matcher = YEAR_PREFIX.matcher(publishedDate);
        if (!matcher.find()) {
            return null;
        }
        return Integer.valueOf(matcher.group(1));
    }

    static @Nullable Integer nullIfZero(final @Nullable Integer pageCount) {
        if (pageCount == null || pageCount == 0) {
            return null;
        }
        return pageCount;
    }

    static @Nullable String stripHtml(final @Nullable String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        final String stripped = HTML_TAG.matcher(text).replaceAll("");
        return stripped
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim();
    }
}
