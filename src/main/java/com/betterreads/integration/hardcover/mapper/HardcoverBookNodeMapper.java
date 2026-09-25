package com.betterreads.integration.hardcover.mapper;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.betterreads.catalog.service.source.model.BookFieldSource;
import com.betterreads.catalog.service.source.merge.SingleBookFilter;
import com.betterreads.catalog.service.source.model.SourceAuthor;
import com.betterreads.catalog.service.source.model.SourceBook;
import com.betterreads.integration.hardcover.dto.HardcoverBookNode;
import org.jspecify.annotations.Nullable;

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

    static int readers(final HardcoverBookNode node) {
        return node.usersCount() == null ? 0 : node.usersCount();
    }

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

    static Optional<SourceBook> toSourceBookWithSeries(final @Nullable HardcoverBookNode node) {
        if (node == null) {
            return Optional.empty();
        }
        return toBuilder(node).map(builder -> HardcoverSeriesVolumes.withSeriesOf(builder, node).build());
    }

    private static boolean isCanonical(final HardcoverBookNode node) {
        return node.canonicalId() == null || node.canonicalId().equals(node.id());
    }

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

    private static @Nullable List<SourceAuthor> authors(final HardcoverBookNode node) {
        final List<HardcoverBookNode.Contribution> contributions = node.contributions();
        return contributions == null ? null : HardcoverContributors.authors(contributions);
    }
}
