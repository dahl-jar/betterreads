package com.betterreads.clients.hardcover;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SingleBookFilter;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import org.jspecify.annotations.Nullable;

public final class HardcoverBookNodeMapper {

    private static final String ENGLISH = "English";

    private static final String AUDIOBOOK_FORMAT = "Listened";

    private static final int GRAPHIC_NOVEL_CATEGORY = 4;

    private static final int COLLECTION_CATEGORY = 8;

    private static final Pattern EDITION_VARIANT =
        Pattern.compile("\\b(?:deluxe|illustrated|annotated|collector'?s|anniversary)\\s+edition\\b",
            Pattern.CASE_INSENSITIVE);

    private HardcoverBookNodeMapper() {
    }

    public static int readers(final HardcoverBookNode node) {
        return node.usersCount() == null ? 0 : node.usersCount();
    }

    private static Optional<SourceBook.Builder> toBuilder(final HardcoverBookNode node) {
        if (!qualifies(node)) {
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
            && isEnglish(node)
            && !isAudiobook(node)
            && !Boolean.TRUE.equals(node.isPartialBook())
            && !isProseCompilation(node)
            && !isOmnibus(node)
            && !EDITION_VARIANT.matcher(title).find()
            && SingleBookFilter.isSingleBook(title)
            && isCanonical(node)
            && !isSingleComicIssue(node);
    }

    private static boolean hasCategory(final HardcoverBookNode node, final int category) {
        return Integer.valueOf(category).equals(node.bookCategoryId());
    }

    private static boolean isOmnibus(final HardcoverBookNode node) {
        return hasCategory(node, COLLECTION_CATEGORY);
    }

    private static boolean isProseCompilation(final HardcoverBookNode node) {
        return Boolean.TRUE.equals(node.compilation()) && !hasCategory(node, GRAPHIC_NOVEL_CATEGORY);
    }

    private static boolean isAudiobook(final HardcoverBookNode node) {
        return Optional.ofNullable(node.defaultPhysicalEdition())
            .map(HardcoverBookNode.Edition::readingFormat)
            .map(HardcoverBookNode.ReadingFormat::format)
            .filter(AUDIOBOOK_FORMAT::equals)
            .isPresent();
    }

    private static boolean isEnglish(final HardcoverBookNode node) {
        return Optional.ofNullable(node.defaultPhysicalEdition())
            .map(HardcoverBookNode.Edition::language)
            .map(HardcoverBookNode.Language::language)
            .filter(ENGLISH::equals)
            .isPresent();
    }

    public static Optional<SourceBook> toSourceBookWithSeries(final @Nullable HardcoverBookNode node) {
        if (node == null) {
            return Optional.empty();
        }
        return toBuilder(node).map(builder -> HardcoverSeriesVolumes.withSeriesOf(builder, node).build());
    }

    private static boolean isCanonical(final HardcoverBookNode node) {
        return node.canonicalId() == null || node.canonicalId().equals(node.id());
    }

    private static boolean isSingleComicIssue(final HardcoverBookNode node) {
        return hasCategory(node, GRAPHIC_NOVEL_CATEGORY) && !Boolean.TRUE.equals(node.compilation());
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
