package com.betterreads.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One award on a book. Each upsert that carries awards replaces the whole list. */
@Entity
@Table(name = "book_award")
// NullAway.Init: JPA sets the fields reflectively.
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

    public Book getBook() {
        return book;
    }

    public String getAward() {
        return award;
    }
}
