package com.betterreads.integration.googlebooks.mapper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.betterreads.catalog.service.BookFieldSource;
import com.betterreads.catalog.service.SourceBook;
import com.betterreads.integration.googlebooks.dto.IndustryIdentifier;
import com.betterreads.integration.googlebooks.dto.Volume;
import com.betterreads.integration.googlebooks.dto.VolumeInfo;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Translates Google Books DTOs to the {@link SourceBook} contract used by the catalog layer.
 *
 * <p>Three Google Books quirks the catalog has to handle live in this mapper. {@code pageCount}
 * is {@code 0} on reprints where Google has metadata but no real page count, so the mapper
 * nulls it. {@code industryIdentifiers} sometimes carries only an {@code ISBN_10}, never
 * synthesized into an ISBN-13. {@code description} ships with embedded HTML
 * ({@code <p>}, {@code <b>}, {@code <i>}, {@code <br>}), stripped before persistence so the
 * tags do not leak into rendered catalog text.
 */
@Component
public class GoogleBooksMapper {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private static final Pattern YEAR_PREFIX = Pattern.compile("^(\\d{4})");

    private static final String ISBN_13_TYPE = "ISBN_13";

    /** Returns the {@link SourceBook} projection of a Google Books volume, or null if unmappable. */
    public @Nullable SourceBook toSourceBook(final @Nullable Volume volume) {
        if (volume == null || volume.volumeInfo() == null) {
            return null;
        }
        final VolumeInfo info = volume.volumeInfo();
        return new SourceBook(
            BookFieldSource.GOOGLE_BOOKS,
            findIsbn13(info.industryIdentifiers()),
            null,
            volume.id(),
            null,
            null,
            null,
            info.title(),
            info.subtitle(),
            stripHtml(info.description()),
            parseYear(info.publishedDate()),
            info.publisher(),
            nullIfZero(info.pageCount()),
            info.language(),
            null,
            info.authors(),
            null,
            info.categories(),
            info.averageRating(),
            info.ratingsCount(),
            null,
            null
        );
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
        if (pageCount == null || pageCount.intValue() == 0) {
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
