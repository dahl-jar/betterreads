package com.betterreads.integration.hardcover.mapper;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.betterreads.catalog.service.source.BookFieldSource;
import com.betterreads.catalog.service.source.SingleBookFilter;
import com.betterreads.catalog.service.source.SourceAuthor;
import com.betterreads.catalog.service.source.SourceBook;
import com.betterreads.integration.hardcover.dto.HardcoverBookNode;
import org.jspecify.annotations.Nullable;

/**
 * Turns a {@link HardcoverBookNode} into a Hardcover-sourced {@link SourceBook} builder.
 *
 * <p>A node qualifies only when it is the canonical English work and not an edition variant: not an
 * audiobook, deluxe or illustrated edition, boxed set, omnibus, split part, or single comic issue. A
 * qualifying node carries forward everything the enumeration returned. The series mapper adds the
 * series name and position to the returned builder; the author mapper applies the book's own featured
 * series.
 */
final class HardcoverBookNodeMapper {

    private static final String ENGLISH = "English";

    private static final String AUDIOBOOK_FORMAT = "Listened";

    private static final int GRAPHIC_NOVEL_CATEGORY = 4;

    private static final int COLLECTION_CATEGORY = 8;

    private static final Pattern EDITION_VARIANT =
        Pattern.compile("\\b(?:deluxe|illustrated|annotated|collector'?s|anniversary)\\s+edition\\b",
            Pattern.CASE_INSENSITIVE);

    private HardcoverBookNodeMapper() {
    }

    /** Returns the node's reader count, or zero when absent, for picking the canonical edition. */
    static int readers(final HardcoverBookNode node) {
        return node.usersCount() == null ? 0 : node.usersCount();
    }

    /** Returns a builder seeded from the node when it is an English canonical single book. */
    static Optional<SourceBook.Builder> toBuilder(final @Nullable HardcoverBookNode node) {
        if (node == null || !qualifies(node)) {
            return Optional.empty();
        }
        return Optional.of(SourceBook.builder(BookFieldSource.HARDCOVER)
            .hardcoverId(node.id() == null ? null : String.valueOf(node.id()))
            .title(node.title())
            .description(node.description())
            .publicationYear(node.releaseYear())
            .coverUrl(coverUrl(node))
            .authors(authors(node))
            .averageRating(node.rating())
            .ratingCount(node.ratingsCount()));
    }

    private static boolean qualifies(final HardcoverBookNode node) {
        final String title = node.title();
        return title != null
            && ENGLISH.equals(language(node))
            && !isAudiobook(node)
            && !Boolean.TRUE.equals(node.isPartialBook())
            && !isCompilation(node)
            && !isOmnibus(node)
            && !EDITION_VARIANT.matcher(title).find()
            && SingleBookFilter.isSingleBook(title)
            && isCanonical(node)
            && !isSingleComicIssue(node);
    }

    private static boolean isOmnibus(final HardcoverBookNode node) {
        final Integer category = node.bookCategoryId();
        return category != null && category == COLLECTION_CATEGORY;
    }

    /**
     * Returns true for a prose bind-up of two or more works, such as "Animal Farm and 1984".
     *
     * <p>Hardcover sets {@code compilation} on a graphic-novel volume too, where it means collected
     * issues that form one book, so a graphic novel is left to {@link #isSingleComicIssue} and only a
     * prose compilation is rejected here.
     */
    private static boolean isCompilation(final HardcoverBookNode node) {
        if (!Boolean.TRUE.equals(node.compilation())) {
            return false;
        }
        final Integer category = node.bookCategoryId();
        return category == null || category != GRAPHIC_NOVEL_CATEGORY;
    }

    private static boolean isAudiobook(final HardcoverBookNode node) {
        final HardcoverBookNode.Edition edition = node.defaultPhysicalEdition();
        if (edition == null || edition.readingFormat() == null) {
            return false;
        }
        return AUDIOBOOK_FORMAT.equals(edition.readingFormat().format());
    }

    /**
     * Returns the built book with its featured series applied, for the author path where the book is
     * not enumerated under one position. The series path sets position itself and uses
     * {@link #toBuilder} instead.
     *
     * <p>The series is applied only when the featured membership carries a numbered volume position.
     * A companion, guide, or anthology is tagged to the series with a null position, which would
     * otherwise stamp a series name with no volume and read as book one.
     */
    static Optional<SourceBook> toSourceBookWithSeries(final @Nullable HardcoverBookNode node) {
        if (node == null) {
            return Optional.empty();
        }
        return toBuilder(node).map(builder -> applyFeaturedSeries(builder, node).build());
    }

    private static SourceBook.Builder applyFeaturedSeries(
        final SourceBook.Builder builder, final HardcoverBookNode node
    ) {
        final List<HardcoverBookNode.SeriesMembership> memberships = node.bookSeries();
        if (memberships == null || memberships.isEmpty()) {
            return builder;
        }
        final HardcoverBookNode.SeriesMembership membership = memberships.stream()
            .filter(entry -> Boolean.TRUE.equals(entry.featured()))
            .findFirst()
            .orElse(memberships.get(0));
        final HardcoverBookNode.Series series = membership.series();
        final Integer position = membership.position();
        if (series == null || series.name() == null || position == null || position < 1) {
            return builder;
        }
        return builder.seriesName(series.name()).seriesPosition(position);
    }

    private static boolean isCanonical(final HardcoverBookNode node) {
        return node.canonicalId() == null || node.canonicalId().equals(node.id());
    }

    /**
     * Returns true for a single graphic-novel issue: category Graphic Novel and not a compilation.
     * A collected volume is the same category but flagged a compilation and is kept; a prose book is
     * a different category and is kept regardless.
     */
    private static boolean isSingleComicIssue(final HardcoverBookNode node) {
        final Integer category = node.bookCategoryId();
        return category != null && category == GRAPHIC_NOVEL_CATEGORY
            && !Boolean.TRUE.equals(node.compilation());
    }

    private static @Nullable String language(final HardcoverBookNode node) {
        final HardcoverBookNode.Edition edition = node.defaultPhysicalEdition();
        if (edition == null || edition.language() == null) {
            return null;
        }
        return edition.language().language();
    }

    private static @Nullable String coverUrl(final HardcoverBookNode node) {
        final HardcoverBookNode.Image image = node.image();
        return image == null ? null : image.url();
    }

    // PMD.ReturnEmptyCollectionRatherThanNull: null means authors omitted, kept distinct from empty.
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
    private static @Nullable List<SourceAuthor> authors(final HardcoverBookNode node) {
        if (node.contributions() == null) {
            return null;
        }
        final List<String> names = node.contributions().stream()
            .map(HardcoverBookNode.Contribution::author)
            .filter(author -> author != null && author.name() != null)
            .map(HardcoverBookNode.Author::name)
            .toList();
        return names.isEmpty() ? null : SourceAuthor.ofNames(names);
    }
}
