package com.betterreads.catalog.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.betterreads.catalog.entity.PendingBook;
import com.betterreads.catalog.service.source.BookField;
import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.MergedBook;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Converts between a {@link MergedBook} and the flat {@link PendingBook} row.
 *
 * <p>The row holds subjects, awards, and authors as newline-joined text and splits them back on
 * read. Newline never appears in a title, name, or canonical genre, so it is a safe delimiter.
 */
@Component
public class PendingBookMapper {

    private static final String LIST_DELIMITER = "\n";

    /** Copies the merged book and its provenance onto the row. */
    public void applyTo(final PendingBook row, final MergedBook merged) {
        final SourceBook book = merged.book();
        final String dedupKey = book.dedupKey();
        if (dedupKey != null) {
            row.setDedupKey(dedupKey);
        }
        row.setIsbn13(book.isbn13());
        row.setOpenLibraryWorkKey(book.openLibraryWorkKey());
        row.setGoogleBooksVolumeId(book.googleBooksVolumeId());
        row.setHardcoverId(book.hardcoverId());
        row.setLocLccn(book.locLccn());
        row.setWikidataQid(book.wikidataQid());
        row.setTitle(book.title());
        row.setSubtitle(book.subtitle());
        row.setDescription(book.description());
        row.setCoverUrl(book.coverUrl());
        row.setFirstPublishYear(book.publicationYear());
        row.setPageCount(book.pageCount());
        row.setLanguage(book.language());
        row.setPublisher(book.publisher());
        row.setAverageRating(toBigDecimal(book.averageRating()));
        row.setRatingCount(book.ratingCount());
        row.setSeriesName(book.seriesName());
        row.setSeriesPosition(book.seriesPosition());
        row.setSubjects(join(book.rawSubjects()));
        row.setAwards(join(book.awards()));
        row.setAuthors(joinAuthors(book.authors()));
        row.setTitleSource(name(merged.provenanceOf(BookField.TITLE)));
        row.setDescriptionSource(name(merged.provenanceOf(BookField.DESCRIPTION)));
        row.setCoverSource(name(merged.provenanceOf(BookField.COVER)));
        row.setPublicationYearSource(name(merged.provenanceOf(BookField.PUBLICATION_YEAR)));
        row.setSubjectsSources(joinSources(merged.subjectSources()));
    }

    /** Rebuilds a {@link SourceBook} from the row for the required-field check and promotion. */
    public SourceBook toSourceBook(final PendingBook row) {
        final List<String> subjects = splitField(row.getSubjects(), Function.identity());
        final List<String> awards = splitField(row.getAwards(), Function.identity());
        final List<SourceAuthor> authors = splitField(row.getAuthors(), SourceAuthor::ofName);
        return SourceBook.builder(BookFieldSource.OPEN_LIBRARY)
            .isbn13(row.getIsbn13())
            .openLibraryWorkKey(row.getOpenLibraryWorkKey())
            .googleBooksVolumeId(row.getGoogleBooksVolumeId())
            .hardcoverId(row.getHardcoverId())
            .locLccn(row.getLocLccn())
            .wikidataQid(row.getWikidataQid())
            .title(row.getTitle())
            .subtitle(row.getSubtitle())
            .description(row.getDescription())
            .coverUrl(row.getCoverUrl())
            .publicationYear(row.getFirstPublishYear())
            .pageCount(row.getPageCount())
            .language(row.getLanguage())
            .publisher(row.getPublisher())
            .averageRating(toDouble(row.getAverageRating()))
            .ratingCount(row.getRatingCount())
            .seriesName(row.getSeriesName())
            .seriesPosition(row.getSeriesPosition())
            .rawSubjects(subjects.isEmpty() ? null : subjects)
            .awards(awards.isEmpty() ? null : awards)
            .authors(authors.isEmpty() ? null : authors)
            .build();
    }

    /** Joins the list for storage, or null when the source did not supply the field. */
    public @Nullable String join(final @Nullable List<String> values) {
        return values == null ? null : String.join(LIST_DELIMITER, values);
    }

    private static <T> List<T> splitField(
        final @Nullable String joined, final Function<String, T> toElement) {
        if (joined == null || joined.isEmpty()) {
            return List.of();
        }
        return List.of(joined.split(LIST_DELIMITER)).stream().map(toElement).toList();
    }

    private static @Nullable String joinAuthors(final @Nullable List<SourceAuthor> authors) {
        return authors == null ? null
            : String.join(LIST_DELIMITER, authors.stream().map(SourceAuthor::name).toList());
    }

    private static @Nullable String joinSources(final Set<BookFieldSource> sources) {
        return sources.isEmpty() ? null
            : String.join(LIST_DELIMITER, sources.stream().map(Enum::name).toList());
    }

    private static @Nullable String name(final @Nullable BookFieldSource source) {
        return source == null ? null : source.name();
    }

    private static @Nullable BigDecimal toBigDecimal(final @Nullable Double rating) {
        return rating == null ? null : BigDecimal.valueOf(rating).setScale(2, RoundingMode.HALF_UP);
    }

    private static @Nullable Double toDouble(final @Nullable BigDecimal rating) {
        return rating == null ? null : rating.doubleValue();
    }
}
