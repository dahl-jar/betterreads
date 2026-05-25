package com.betterreads.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import com.betterreads.catalog.service.SourceBook;
import org.jspecify.annotations.Nullable;

/**
 * Maps to {@code book}. The two source-id columns ({@code openLibraryWorkKey},
 * {@code googleBooksVolumeId}) are independently unique-nullable; either one identifies a row.
 * The catalog table is fed from external sources via the multi-source pipeline; this slice
 * exercises only the Google Books inserts.
 */
@Entity
@Table(name = "book")
@SuppressWarnings({"NullAway.Init", "PMD.DataClass", "PMD.ExcessivePublicCount", "PMD.TooManyFields"})
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "open_library_work_key", unique = true)
    @Nullable
    private String openLibraryWorkKey;

    @Column(name = "google_books_volume_id", unique = true)
    @Nullable
    private String googleBooksVolumeId;

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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToMany
    @JoinTable(
        name = "book_author",
        joinColumns = @JoinColumn(name = "book_id"),
        inverseJoinColumns = @JoinColumn(name = "author_id")
    )
    private Set<Author> authors = new HashSet<>();

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
     * Overwrites every column on this book with the values from {@code source}, including nulls.
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
        this.googleBooksVolumeId = source.googleBooksVolumeId();
        this.openLibraryWorkKey = source.openLibraryWorkKey();
        this.subtitle = source.subtitle();
        this.description = source.description();
        this.coverUrl = source.coverUrl();
        this.firstPublishYear = source.publicationYear();
        this.isbn = source.isbn13();
        this.pageCount = source.pageCount();
        this.language = source.language();
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
