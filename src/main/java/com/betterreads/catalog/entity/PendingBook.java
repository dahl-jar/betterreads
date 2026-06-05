package com.betterreads.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jspecify.annotations.Nullable;

/**
 * Maps to {@code pending_book}, the staging table for books not yet complete enough to show. A
 * candidate accumulates merged metadata across runs and is promoted into {@code book} once it
 * carries every required field. Subjects, awards, and authors are held as delimited text here and
 * split into the normalized child tables at promotion.
 */
@Entity
@Table(name = "pending_book")
@SuppressWarnings({
    "NullAway.Init", "PMD.ExcessivePublicCount", "PMD.TooManyFields", "PMD.CyclomaticComplexity"
})
public class PendingBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pending_book_id")
    private Long pendingBookId;

    @Column(name = "dedup_key", nullable = false, unique = true)
    private String dedupKey;

    @Column(name = "isbn13", unique = true)
    @Nullable
    private String isbn13;

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

    @Column(name = "title")
    @Nullable
    private String title;

    @Column(name = "subtitle")
    @Nullable
    private String subtitle;

    @Column(name = "description")
    @Nullable
    private String description;

    @Column(name = "cover_url")
    @Nullable
    private String coverUrl;

    @Column(name = "first_publish_year")
    @Nullable
    private Integer firstPublishYear;

    @Column(name = "page_count")
    @Nullable
    private Integer pageCount;

    @Column(name = "language")
    @Nullable
    private String language;

    @Column(name = "publisher")
    @Nullable
    private String publisher;

    @Column(name = "average_rating", precision = 3, scale = 2)
    @Nullable
    private BigDecimal averageRating;

    @Column(name = "rating_count")
    @Nullable
    private Integer ratingCount;

    @Column(name = "series_name")
    @Nullable
    private String seriesName;

    @Column(name = "series_position")
    @Nullable
    private Integer seriesPosition;

    @Column(name = "subjects")
    @Nullable
    private String subjects;

    @Column(name = "awards")
    @Nullable
    private String awards;

    @Column(name = "authors")
    @Nullable
    private String authors;

    @Column(name = "title_source")
    @Nullable
    private String titleSource;

    @Column(name = "description_source")
    @Nullable
    private String descriptionSource;

    @Column(name = "cover_source")
    @Nullable
    private String coverSource;

    @Column(name = "publication_year_source")
    @Nullable
    private String publicationYearSource;

    @Column(name = "subjects_sources")
    @Nullable
    private String subjectsSources;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "missing_fields")
    @Nullable
    private String missingFields;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "first_seen_at", nullable = false)
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_attempt_at")
    @Nullable
    private OffsetDateTime lastAttemptAt;

    @PrePersist
    void onCreate() {
        if (this.firstSeenAt == null) {
            this.firstSeenAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getPendingBookId() {
        return pendingBookId;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(final String dedupKey) {
        this.dedupKey = dedupKey;
    }

    @Nullable
    public String getIsbn13() {
        return isbn13;
    }

    public void setIsbn13(@Nullable final String isbn13) {
        this.isbn13 = isbn13;
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

    @Nullable
    public String getWikidataQid() {
        return wikidataQid;
    }

    public void setWikidataQid(@Nullable final String wikidataQid) {
        this.wikidataQid = wikidataQid;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    public void setTitle(@Nullable final String title) {
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
    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(@Nullable final String publisher) {
        this.publisher = publisher;
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

    @Nullable
    public String getSubjects() {
        return subjects;
    }

    public void setSubjects(@Nullable final String subjects) {
        this.subjects = subjects;
    }

    @Nullable
    public String getAwards() {
        return awards;
    }

    public void setAwards(@Nullable final String awards) {
        this.awards = awards;
    }

    @Nullable
    public String getAuthors() {
        return authors;
    }

    public void setAuthors(@Nullable final String authors) {
        this.authors = authors;
    }

    @Nullable
    public String getTitleSource() {
        return titleSource;
    }

    public void setTitleSource(@Nullable final String titleSource) {
        this.titleSource = titleSource;
    }

    @Nullable
    public String getDescriptionSource() {
        return descriptionSource;
    }

    public void setDescriptionSource(@Nullable final String descriptionSource) {
        this.descriptionSource = descriptionSource;
    }

    @Nullable
    public String getCoverSource() {
        return coverSource;
    }

    public void setCoverSource(@Nullable final String coverSource) {
        this.coverSource = coverSource;
    }

    @Nullable
    public String getPublicationYearSource() {
        return publicationYearSource;
    }

    public void setPublicationYearSource(@Nullable final String publicationYearSource) {
        this.publicationYearSource = publicationYearSource;
    }

    @Nullable
    public String getSubjectsSources() {
        return subjectsSources;
    }

    public void setSubjectsSources(@Nullable final String subjectsSources) {
        this.subjectsSources = subjectsSources;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    @Nullable
    public String getMissingFields() {
        return missingFields;
    }

    public void setMissingFields(@Nullable final String missingFields) {
        this.missingFields = missingFields;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(final int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public OffsetDateTime getFirstSeenAt() {
        return firstSeenAt;
    }

    @Nullable
    public OffsetDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(@Nullable final OffsetDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }
}
