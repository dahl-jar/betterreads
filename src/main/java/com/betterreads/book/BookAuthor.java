package com.betterreads.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.betterreads.booksource.CreditRole;

@Entity
@Table(name = "book_author")
// NullAway.Init: JPA sets the fields reflectively.
@SuppressWarnings("NullAway.Init")
@IdClass(BookAuthorId.class)
public class BookAuthor {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Author author;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private CreditRole role;

    @Column(name = "position", nullable = false)
    private int position;

    protected BookAuthor() {
    }

    BookAuthor(final Book book, final Author author, final CreditRole role, final int position) {
        this.book = book;
        this.author = author;
        this.role = role;
        this.position = position;
    }

    public Author getAuthor() {
        return author;
    }

    public CreditRole getRole() {
        return role;
    }

    public int getPosition() {
        return position;
    }

    boolean place(final CreditRole newRole, final int newPosition) {
        final boolean changed = role != newRole || position != newPosition;
        this.role = newRole;
        this.position = newPosition;
        return changed;
    }
}
