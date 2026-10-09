package com.betterreads.clients.hardcover;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.betterreads.booksource.BookFieldSource;
import com.betterreads.booksource.SingleBookFilter;
import com.betterreads.booksource.SourceAuthor;
import com.betterreads.booksource.SourceBook;
import com.betterreads.isbn.Isbn13;
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

    private static SourceBook.Builder toBuilder(final HardcoverBookNode node) {
        return SourceBook.builder(BookFieldSource.HARDCOVER)
            .hardcoverId(node.id() == null ? null : String.valueOf(node.id()))
            .title(node.title())
            .description(node.description())
            .publicationYear(node.releaseYear())
            .coverUrl(coverUrl(node))
            .authors(authors(node))
            .averageRating(node.rating())
            .ratingCount(node.ratingsCount());
    }

    private static boolean qualifies(final HardcoverBookNode node) {
        return !isProseCompilation(node.compilation(), node.bookCategoryId()) && qualifiesAllowingCompilation(node);
    }

    private static boolean qualifiesAllowingCompilation(final HardcoverBookNode node) {
        final String title = node.title();
        return title != null
            && isEnglish(node)
            && !isAudiobook(node)
            && !Boolean.TRUE.equals(node.isPartialBook())
            && !isOmnibus(node)
            && !EDITION_VARIANT.matcher(title).find()
            && SingleBookFilter.isSingleBook(title)
            && isCanonical(node)
            && !isSingleComicIssue(node);
    }

    private static boolean isCategory(final @Nullable Integer bookCategoryId, final int category) {
        return Integer.valueOf(category).equals(bookCategoryId);
    }

    private static boolean isOmnibus(final HardcoverBookNode node) {
        return isCategory(node.bookCategoryId(), COLLECTION_CATEGORY);
    }

    public static boolean isProseCompilation(
        final @Nullable Boolean compilation,
        final @Nullable Integer bookCategoryId
    ) {
        return Boolean.TRUE.equals(compilation) && !isCategory(bookCategoryId, GRAPHIC_NOVEL_CATEGORY);
    }

    private static Optional<HardcoverBookNode.Edition> edition(final HardcoverBookNode node) {
        return Optional.ofNullable(node.defaultPhysicalEdition());
    }

    private static boolean isAudiobook(final HardcoverBookNode node) {
        return edition(node)
            .map(HardcoverBookNode.Edition::readingFormat)
            .map(HardcoverBookNode.ReadingFormat::format)
            .filter(AUDIOBOOK_FORMAT::equals)
            .isPresent();
    }

    private static boolean isEnglish(final HardcoverBookNode node) {
        return edition(node)
            .map(HardcoverBookNode.Edition::language)
            .map(HardcoverBookNode.Language::language)
            .filter(ENGLISH::equals)
            .isPresent();
    }

    public static Optional<SourceBook> toSourceBookWithSeries(final @Nullable HardcoverBookNode node) {
        return Optional.ofNullable(node)
            .filter(HardcoverBookNodeMapper::qualifies)
            .map(accepted -> HardcoverSeriesVolumes.withSeriesOf(toBuilder(accepted), accepted).build());
    }

    public static Optional<SourceBook> toTitleBook(final @Nullable HardcoverBookNode node) {
        return Optional.ofNullable(node)
            .filter(HardcoverBookNodeMapper::qualifiesAllowingCompilation)
            .map(accepted -> toBuilder(accepted).isbn13(defaultEditionIsbn(accepted)).build());
    }

    private static @Nullable String defaultEditionIsbn(final HardcoverBookNode node) {
        return edition(node)
            .map(HardcoverBookNode.Edition::isbn13)
            .filter(Isbn13::isValid)
            .orElse(null);
    }

    private static boolean isCanonical(final HardcoverBookNode node) {
        return node.canonicalId() == null || node.canonicalId().equals(node.id());
    }

    private static boolean isSingleComicIssue(final HardcoverBookNode node) {
        return isCategory(node.bookCategoryId(), GRAPHIC_NOVEL_CATEGORY) && !Boolean.TRUE.equals(node.compilation());
    }

    private static @Nullable String coverUrl(final HardcoverBookNode node) {
        final HardcoverBookNode.Image image = node.image();
        return image == null ? null : image.url();
    }

    private static @Nullable List<SourceAuthor> authors(final HardcoverBookNode node) {
        final List<HardcoverBookNode.Contribution> contributions = node.contributions();
        return contributions == null ? null : HardcoverContributors.credits(contributions);
    }
}
