package com.betterreads.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One genre subject on a book. Each upsert that carries subjects replaces the whole list. */
@Entity
@Table(name = "book_subject")
// NullAway.Init: JPA sets the fields reflectively.
@SuppressWarnings("NullAway.Init")
public class BookSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_subject_id")
    private Long bookSubjectId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "subject", nullable = false)
    private String subject;

    protected BookSubject() {
    }

    BookSubject(final Book book, final String subject) {
        this.book = book;
        this.subject = subject;
    }

    public Book getBook() {
        return book;
    }

    public String getSubject() {
        return subject;
    }
}
