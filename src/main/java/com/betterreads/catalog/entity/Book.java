package com.betterreads.catalog.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import com.betterreads.catalog.service.source.SourceBook;
import org.jspecify.annotations.Nullable;

/**
 * Maps to {@code book}. Each source-id column ({@code openLibraryWorkKey},
 * {@code googleBooksVolumeId}, {@code hardcoverId}, {@code locLccn}) is independently
 * unique-nullable; any one identifies a row.
 */
@Entity
@Table(name = "book")
@SuppressWarnings({
    "NullAway.Init", "PMD.ExcessivePublicCount", "PMD.TooManyFields",
    "PMD.CyclomaticComplexity"
})
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "dedup_key", nullable = false, unique = true)
    private String dedupKey;

    @Column(name = "open_library_work_key", unique = true)
    @Nullable
    private String openLibraryWorkKey;

    @Column(name = "google_books_volume_id", unique = true)
    @Nullable
    private String googleBooksVolumeId;

    @Column(name = "hardcover_id", unique = true)
    @Nullable
    private String hardcoverId;

    @Column(name = "loc_lccn", unique = true)
    @Nullable
    private String locLccn;

    @Column(name = "wikidata_qid", unique = true)
    @Nullable
    private String wikidataQid;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "subtitle")
    @Nullable
    private String subtitle;

    @Column(name = "description", columnDefinition = "TEXT")
    @Nullable
    private String description;

    @Column(name = "cover_id")
    @Nullable
    private Integer coverId;

    @Column(name = "cover_url")
    @Nullable
    private String coverUrl;

    @Column(name = "cover_object_key")
    @Nullable
    private String coverObjectKey;

    @Nullable
    @Column(name = "cover_checked_at")
    private OffsetDateTime coverCheckedAt;

    @Column(name = "first_publish_year")
    @Nullable
    private Integer firstPublishYear;

    @Column(name = "isbn", length = 20)
    @Nullable
    private String isbn;

    @Column(name = "page_count")
    @Nullable
    private Integer pageCount;

    @Column(name = "language", length = 10)
    @Nullable
    private String language;

    @Column(name = "average_rating", precision = 3, scale = 2)
    @Nullable
    private BigDecimal averageRating;

    @Column(name = "rating_count")
    @Nullable
    private Integer ratingCount;

    @Column(name = "community_average", precision = 3, scale = 2)
    @Nullable
    private BigDecimal communityAverage;

    @Column(name = "community_count", nullable = false)
    private int communityCount;

    @Column(name = "series_name")
    @Nullable
    private String seriesName;

    @Column(name = "series_position")
    @Nullable
    private Integer seriesPosition;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Nullable
    @Column(name = "description_checked_at")
    private OffsetDateTime descriptionCheckedAt;

    @ManyToMany
    @JoinTable(
        name = "book_author",
        joinColumns = @JoinColumn(name = "book_id"),
        inverseJoinColumns = @JoinColumn(name = "author_id")
    )
    private Set<Author> authors = new HashSet<>();

    /**
     * A fetch that joins authors and subjects in one query repeats each subject row per author; the
     * {@code Set} collapses the repeats by entity identity, and {@code @OrderBy} keeps row order.
     */
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("bookSubjectId")
    private final Set<BookSubject> subjects = new LinkedHashSet<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<BookAward> awards = new ArrayList<>();

    @PrePersist
    void onCreate() {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Applies {@code source} to this book. Descriptive columns (title, description, cover, year,
     * isbn, pages, language, subjects) overwrite with the source value including null; source ids
     * and the rating columns are filled only when the source supplies them, via {@link #accrueFrom}.
     * The series columns are not touched here; {@link #applySeries} sets them with its own guard.
     *
     * <p>Authors are not touched here; the catalog service owns the {@code book_author} join
     * because attaching authors requires repository lookups for de-duplication.
     *
     * @throws IllegalArgumentException if {@code source} has no title
     */
    public void applyFrom(final SourceBook source) {
        final String sourceTitle = source.title();
        if (sourceTitle == null) {
            throw new IllegalArgumentException(
                "SourceBook without a title cannot become a catalog Book; "
                    + "the upstream source returned a degenerate response");
        }
        this.title = sourceTitle;
        this.subtitle = source.subtitle();
        this.description = source.description();
        invalidateMirrorIfCoverChanged(source.coverUrl());
        this.coverUrl = source.coverUrl();
        this.firstPublishYear = source.publicationYear();
        this.isbn = source.isbn13();
        this.pageCount = source.pageCount();
        this.language = source.language();
        replaceSubjects(source.rawSubjects());
        replaceAwards(source.awards());
        accrueFrom(source);
        assignDedupKey();
    }

    /**
     * Clears the mirrored object when the source cover URL changes, so the image endpoint and backfill
     * re-mirror instead of serving the cover stored for the old URL.
     */
    // PMD.NullAssignment: nulling the mirror-state columns is how "not mirrored" is recorded
    @SuppressWarnings("PMD.NullAssignment")
    private void invalidateMirrorIfCoverChanged(final @Nullable String newCoverUrl) {
        if (!Objects.equals(this.coverUrl, newCoverUrl)) {
            this.coverObjectKey = null;
            this.coverCheckedAt = null;
        }
    }

    /**
     * Sets the public key once, to the first present source identifier in the staging order. A later
     * source supplying a higher-precedence id does not change it.
     *
     * @throws IllegalArgumentException if the source carries no identifier to key the book on
     */
    private void assignDedupKey() {
        if (this.dedupKey != null) {
            return;
        }
        this.dedupKey = Stream.of(
                this.isbn, this.openLibraryWorkKey, this.googleBooksVolumeId,
                this.hardcoverId, this.locLccn, this.wikidataQid)
            .filter(id -> id != null)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "book has no source identifier to key on; cannot promote a book with no id"));
    }

    /**
     * Fills source ids and the rating fields without overwriting a set value with null.
     *
     * <p>The source rating lives in {@code averageRating} and {@code ratingCount} and the reader
     * rating in {@code communityAverage} and {@code communityCount}, so enrichment refreshes the
     * source rating freely without touching the reader aggregate. The series is set separately by
     * {@link #applySeries} because clearing it depends on whether its authority resolved.
     */
    private void accrueFrom(final SourceBook source) {
        this.googleBooksVolumeId = coalesce(source.googleBooksVolumeId(), this.googleBooksVolumeId);
        this.openLibraryWorkKey = coalesce(source.openLibraryWorkKey(), this.openLibraryWorkKey);
        this.hardcoverId = coalesce(source.hardcoverId(), this.hardcoverId);
        this.locLccn = coalesce(source.locLccn(), this.locLccn);
        this.wikidataQid = coalesce(source.wikidataQid(), this.wikidataQid);
        this.averageRating = coalesce(toRating(source.averageRating()), this.averageRating);
        this.ratingCount = coalesce(source.ratingCount(), this.ratingCount);
    }

    /**
     * Sets the series name and position when {@code authorityResolved}, clearing both when the
     * resolved authority reported no volume, and leaving the stored series untouched otherwise.
     *
     * <p>Series does not use the fill-only-if-null accrual the rating and ids use: clearing a
     * mislabelled series needs a null to overwrite, but a clear is trusted only when the authority
     * resolved, so a failed or timed-out collect does not wipe a real series.
     */
    public void applySeries(
        final @Nullable String name, final @Nullable Integer position, final boolean authorityResolved) {
        if (!authorityResolved) {
            return;
        }
        this.seriesName = name;
        this.seriesPosition = position;
    }

    private static <T> @Nullable T coalesce(final @Nullable T value, final @Nullable T fallback) {
        return value == null ? fallback : value;
    }

    private static @Nullable BigDecimal toRating(final @Nullable Double rating) {
        return rating == null ? null : BigDecimal.valueOf(rating).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Replaces the book's subjects with the given list, removing any that are no longer present.
     *
     * <p>Re-upserting a book reapplies the source, so subjects are cleared and rebuilt rather than
     * appended; {@code orphanRemoval} deletes the dropped rows. A null list means the source did
     * not return the field (for example an OpenLibrary work-detail 4xx), so the existing subjects
     * are left untouched; an empty list is an explicit "no subjects" and clears them.
     */
    private void replaceSubjects(@Nullable final List<String> newSubjects) {
        if (newSubjects == null) {
            return;
        }
        this.subjects.clear();
        for (final String subject : newSubjects) {
            this.subjects.add(new BookSubject(this, subject));
        }
    }

    /**
     * Replaces the book's awards with the given list, removing any no longer present.
     *
     * <p>A null list leaves the existing awards untouched, since a source that does not carry awards
     * must not wipe another source's. An empty list clears them.
     */
    private void replaceAwards(@Nullable final List<String> newAwards) {
        if (newAwards == null) {
            return;
        }
        this.awards.clear();
        for (final String award : newAwards) {
            this.awards.add(new BookAward(this, award));
        }
    }

    public Set<BookSubject> getSubjects() {
        return subjects;
    }

    public List<BookAward> getAwards() {
        return awards;
    }

    @Nullable
    public String getWikidataQid() {
        return wikidataQid;
    }

    public void setWikidataQid(@Nullable final String wikidataQid) {
        this.wikidataQid = wikidataQid;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(final String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(final Long bookId) {
        this.bookId = bookId;
    }

    @Nullable
    public String getOpenLibraryWorkKey() {
        return openLibraryWorkKey;
    }

    public void setOpenLibraryWorkKey(@Nullable final String openLibraryWorkKey) {
        this.openLibraryWorkKey = openLibraryWorkKey;
    }

    @Nullable
    public String getGoogleBooksVolumeId() {
        return googleBooksVolumeId;
    }

    public void setGoogleBooksVolumeId(@Nullable final String googleBooksVolumeId) {
        this.googleBooksVolumeId = googleBooksVolumeId;
    }

    @Nullable
    public String getHardcoverId() {
        return hardcoverId;
    }

    public void setHardcoverId(@Nullable final String hardcoverId) {
        this.hardcoverId = hardcoverId;
    }

    @Nullable
    public String getLocLccn() {
        return locLccn;
    }

    public void setLocLccn(@Nullable final String locLccn) {
        this.locLccn = locLccn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    @Nullable
    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(@Nullable final String subtitle) {
        this.subtitle = subtitle;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable final String description) {
        this.description = description;
    }

    @Nullable
    public Integer getCoverId() {
        return coverId;
    }

    public void setCoverId(@Nullable final Integer coverId) {
        this.coverId = coverId;
    }

    @Nullable
    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(@Nullable final String coverUrl) {
        this.coverUrl = coverUrl;
    }

    @Nullable
    public String getCoverObjectKey() {
        return coverObjectKey;
    }

    public void setCoverObjectKey(@Nullable final String coverObjectKey) {
        this.coverObjectKey = coverObjectKey;
    }

    @Nullable
    public Integer getFirstPublishYear() {
        return firstPublishYear;
    }

    public void setFirstPublishYear(@Nullable final Integer firstPublishYear) {
        this.firstPublishYear = firstPublishYear;
    }

    @Nullable
    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(@Nullable final String isbn) {
        this.isbn = isbn;
    }

    @Nullable
    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(@Nullable final Integer pageCount) {
        this.pageCount = pageCount;
    }

    @Nullable
    public String getLanguage() {
        return language;
    }

    public void setLanguage(@Nullable final String language) {
        this.language = language;
    }

    @Nullable
    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(@Nullable final BigDecimal averageRating) {
        this.averageRating = averageRating;
    }

    @Nullable
    public Integer getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(@Nullable final Integer ratingCount) {
        this.ratingCount = ratingCount;
    }

    @Nullable
    public BigDecimal getCommunityAverage() {
        return communityAverage;
    }

    public int getCommunityCount() {
        return communityCount;
    }

    /**
     * Sets the reader-community rating, leaving the source rating columns untouched.
     *
     * @param average the mean of user ratings, null when no rated review remains
     * @param count the number of user ratings, zero when the last rating was removed
     */
    public void applyCommunityAggregate(@Nullable final BigDecimal average, final int count) {
        this.communityAverage = average;
        this.communityCount = count;
    }

    @Nullable
    public String getSeriesName() {
        return seriesName;
    }

    public void setSeriesName(@Nullable final String seriesName) {
        this.seriesName = seriesName;
    }

    @Nullable
    public Integer getSeriesPosition() {
        return seriesPosition;
    }

    public void setSeriesPosition(@Nullable final Integer seriesPosition) {
        this.seriesPosition = seriesPosition;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Set<Author> getAuthors() {
        return authors;
    }

    public void setAuthors(final Set<Author> authors) {
        this.authors = authors;
    }
}
