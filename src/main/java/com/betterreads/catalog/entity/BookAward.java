package com.betterreads.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One award on a {@link Book}, mapped to {@code book_award}.
 *
 * <p>Each row belongs to one book and is replaced wholesale on re-upsert. The owning {@code Book}
 * cascades persist and removal.
 */
@Entity
@Table(name = "book_award")
@SuppressWarnings("NullAway.Init")
public class BookAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_award_id")
    private Long bookAwardId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "award", nullable = false)
    private String award;

    protected BookAward() {
    }

    BookAward(final Book book, final String award) {
        this.book = book;
        this.award = award;
    }

    public Long getBookAwardId() {
        return bookAwardId;
    }

    public Book getBook() {
        return book;
    }

    public String getAward() {
        return award;
    }
}
