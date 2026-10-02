package com.betterreads.book;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.betterreads.booksource.SeriesEntry;
import com.betterreads.booksource.SourceBook;
import com.betterreads.db.Timestamped;
import org.jspecify.annotations.Nullable;

/** A catalog book. Each source id column is unique and nullable, and any one of them identifies the row. */
@Entity
@Table(name = "book")
// NullAway.Init, PMD.ExcessivePublicCount, PMD.TooManyFields, PMD.CyclomaticComplexity: JPA sets one field per column.
@SuppressWarnings({
    "NullAway.Init", "PMD.ExcessivePublicCount", "PMD.TooManyFields",
    "PMD.CyclomaticComplexity"
})
public class Book extends Timestamped {

    private static final String ENGLISH = "en";

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

    @Nullable
    @Column(name = "description_checked_at")
    private OffsetDateTime descriptionCheckedAt;

    @Nullable
    @Column(name = "metadata_checked_at")
    private OffsetDateTime metadataCheckedAt;

    @Convert(converter = VerifiedFieldsConverter.class)
    @Column(name = "verified_fields", nullable = false)
    private final Set<VerifiedField> verifiedFields = EnumSet.noneOf(VerifiedField.class);

    @ManyToMany
    @JoinTable(
        name = "book_author",
        joinColumns = @JoinColumn(name = "book_id"),
        inverseJoinColumns = @JoinColumn(name = "author_id")
    )
    private Set<Author> authors = new HashSet<>();

    /**
     * A fetch that joins authors and subjects in one query repeats each subject row per author. The
     * {@code Set} collapses the repeats by entity identity, and {@code @OrderBy} keeps row order.
     */
    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("bookSubjectId")
    private final Set<BookSubject> subjects = new LinkedHashSet<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<BookAward> awards = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "book_series", joinColumns = @JoinColumn(name = "book_id"))
    @OrderColumn(name = "ordinal")
    private final List<BookSeries> series = new ArrayList<>();

    /**
     * Overwrites the descriptive fields, null included. Source ids, ratings, subjects and awards
     * change only when the source has them.
     *
     * @throws IllegalArgumentException if the source has no title or no source id
     */
    public void applyFrom(final SourceBook source) {
        final String sourceTitle = source.title();
        if (sourceTitle == null) {
            throw new IllegalArgumentException("source book has no title");
        }
        this.title = isVerified(VerifiedField.TITLE) ? this.title : sourceTitle;
        this.subtitle = source.subtitle();
        this.description = isVerified(VerifiedField.DESCRIPTION) ? this.description : source.description();
        invalidateMirrorIfCoverChanged(source.coverUrl());
        this.coverUrl = source.coverUrl();
        this.firstPublishYear = isVerified(VerifiedField.YEAR) ? this.firstPublishYear : source.publicationYear();
        this.isbn = isVerified(VerifiedField.ISBN) ? this.isbn : source.isbn13();
        this.pageCount = source.pageCount();
        this.language = isVerified(VerifiedField.ISBN) ? this.language : source.language();
        replaceSubjects(source.rawSubjects());
        replaceAwards(source.awards());
        accrueFrom(source);
        assignDedupKey();
    }

    /** A changed cover URL clears the mirrored copy, so the image stored for the old URL is not served. */
    // PMD.NullAssignment: nulling the mirror-state columns is how "not mirrored" is recorded
    @SuppressWarnings("PMD.NullAssignment")
    private void invalidateMirrorIfCoverChanged(final @Nullable String newCoverUrl) {
        if (!Objects.equals(this.coverUrl, newCoverUrl)) {
            this.coverObjectKey = null;
            this.coverCheckedAt = null;
        }
    }

    /**
     * The dedup key is the public book key, so it never changes once set, even when a
     * higher-precedence id arrives later.
     */
    private void assignDedupKey() {
        if (this.dedupKey != null) {
            return;
        }
        this.dedupKey = Stream.of(
                this.isbn, this.openLibraryWorkKey, this.googleBooksVolumeId,
                this.hardcoverId, this.locLccn, this.wikidataQid)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "book has no source identifier to key on"));
    }

    private void accrueFrom(final SourceBook source) {
        this.googleBooksVolumeId = coalesce(source.googleBooksVolumeId(), this.googleBooksVolumeId);
        this.openLibraryWorkKey = coalesce(source.openLibraryWorkKey(), this.openLibraryWorkKey);
        this.hardcoverId = coalesce(source.hardcoverId(), this.hardcoverId);
        this.locLccn = coalesce(source.locLccn(), this.locLccn);
        this.wikidataQid = coalesce(source.wikidataQid(), this.wikidataQid);
        this.averageRating = coalesce(source.roundedAverageRating(), this.averageRating);
        this.ratingCount = coalesce(source.ratingCount(), this.ratingCount);
    }

    /**
     * A clear is trusted only when the series authority resolved, so a failed or timed-out collect
     * does not wipe a real series. A verified series is kept.
     */
    public void applySeries(final List<SeriesEntry> entries, final boolean authorityResolved) {
        if (!authorityResolved || isVerified(VerifiedField.SERIES)) {
            return;
        }
        final Optional<SeriesEntry> primary = entries.stream().findFirst();
        this.seriesName = primary.map(SeriesEntry::name).orElse(null);
        this.seriesPosition = primary.map(SeriesEntry::position).orElse(null);
        replaceSeries(entries);
    }

    private void replaceSeries(final List<SeriesEntry> entries) {
        final List<BookSeries> rows = entries.stream().map(BookSeries::from).toList();
        if (rows.equals(this.series)) {
            return;
        }
        this.series.clear();
        this.series.addAll(rows);
        stampUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    public void applyVerified(final VerifiedMetadata metadata, final OffsetDateTime checkedAt) {
        final String verifiedTitle = metadata.title();
        if (metadata.authors() != null) {
            verifiedFields.add(VerifiedField.AUTHORS);
        }
        if (verifiedTitle != null) {
            this.title = verifiedTitle;
            verifiedFields.add(VerifiedField.TITLE);
        }
        if (metadata.year() != null) {
            this.firstPublishYear = metadata.year();
            verifiedFields.add(VerifiedField.YEAR);
        }
        if (metadata.seriesName() != null) {
            this.seriesName = metadata.seriesName();
            this.seriesPosition = metadata.seriesPosition();
            replaceSeries(SeriesEntry.listOf(metadata.seriesName(), metadata.seriesPosition()));
            verifiedFields.add(VerifiedField.SERIES);
        }
        if (metadata.description() != null) {
            this.description = metadata.description();
            verifiedFields.add(VerifiedField.DESCRIPTION);
        }
        if (metadata.isbn13() != null) {
            this.isbn = metadata.isbn13();
            this.language = ENGLISH;
            verifiedFields.add(VerifiedField.ISBN);
        }
        this.metadataCheckedAt = checkedAt;
    }

    public Set<VerifiedField> getVerifiedFields() {
        return Set.copyOf(verifiedFields);
    }

    boolean isVerified(final VerifiedField field) {
        return verifiedFields.contains(field);
    }

    private static <T> @Nullable T coalesce(final @Nullable T value, final @Nullable T fallback) {
        return value == null ? fallback : value;
    }

    /**
     * A null list means the source did not return the field, for example on an OpenLibrary
     * work-detail 4xx, so the stored subjects stay. An empty list clears them.
     */
    private void replaceSubjects(@Nullable final List<String> newSubjects) {
        if (newSubjects == null) {
            return;
        }
        this.subjects.clear();
        this.subjects.addAll(newSubjects.stream().map(subject -> new BookSubject(this, subject)).toList());
    }

    /**
     * Most sources carry no awards, so a null list keeps the ones another source stored. An empty
     * list clears them.
     */
    private void replaceAwards(@Nullable final List<String> newAwards) {
        if (newAwards == null) {
            return;
        }
        this.awards.clear();
        this.awards.addAll(newAwards.stream().map(award -> new BookAward(this, award)).toList());
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

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable final String description) {
        this.description = description;
    }

    @Nullable
    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(@Nullable final String coverUrl) {
        this.coverUrl = coverUrl;
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

    @Nullable
    public String getLanguage() {
        return language;
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

    /** The average is null once no rated review remains. */
    public void applyCommunityAggregate(@Nullable final BigDecimal average, final int count) {
        this.communityAverage = average;
        this.communityCount = count;
    }

    @Nullable
    public String getSeriesName() {
        return seriesName;
    }

    @Nullable
    public Integer getSeriesPosition() {
        return seriesPosition;
    }

    public List<SeriesEntry> getSeries() {
        return series.stream().map(BookSeries::toEntry).toList();
    }

    void setUpdatedAt(final OffsetDateTime updatedAt) {
        stampUpdatedAt(updatedAt);
    }

    public Set<Author> getAuthors() {
        return authors;
    }

    public void setAuthors(final Set<Author> authors) {
        this.authors = authors;
    }
}
