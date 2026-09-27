package com.betterreads.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "author")
// NullAway.Init, PMD.DataClass: JPA sets the fields reflectively and an entity is a data holder.
@SuppressWarnings({"NullAway.Init", "PMD.DataClass"})
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "open_library_key", unique = true)
    @Nullable
    private String openLibraryKey;

    @Column(name = "wikidata_qid", unique = true)
    @Nullable
    private String wikidataQid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "photo_url", columnDefinition = "TEXT")
    @Nullable
    private String photoUrl;

    @Column(name = "bio", columnDefinition = "TEXT")
    @Nullable
    private String bio;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Nullable
    public String getWikidataQid() {
        return wikidataQid;
    }

    public void setWikidataQid(@Nullable final String wikidataQid) {
        this.wikidataQid = wikidataQid;
    }

    @Nullable
    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(@Nullable final String photoUrl) {
        this.photoUrl = photoUrl;
    }

    @Nullable
    public String getBio() {
        return bio;
    }

    public void setBio(@Nullable final String bio) {
        this.bio = bio;
    }

    public static List<String> sortedNames(final Collection<Author> authors) {
        return authors.stream().map(Author::getName).sorted().toList();
    }
}
