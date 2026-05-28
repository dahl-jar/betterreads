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
 * One genre subject on a {@link Book}, mapped to {@code book_subject}.
 *
 * <p>Unlike authors, subjects are not shared between books: each row belongs to one book and gets
 * replaced wholesale on re-upsert. The owning {@code Book} cascades persist and removal, so this
 * has no lifecycle of its own.
 */
@Entity
@Table(name = "book_subject")
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

    public Long getBookSubjectId() {
        return bookSubjectId;
    }

    public Book getBook() {
        return book;
    }

    public String getSubject() {
        return subject;
    }
}
