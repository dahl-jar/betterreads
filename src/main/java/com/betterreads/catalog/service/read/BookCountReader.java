package com.betterreads.catalog.service.read;

import com.betterreads.catalog.dto.BookCountResponse;
import com.betterreads.catalog.repository.BookListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookCountReader {

    private final BookListRepository books;

    public BookCountReader(final BookListRepository books) {
        this.books = books;
    }

    @Transactional(readOnly = true)
    public BookCountResponse count() {
        return new BookCountResponse(books.count());
    }
}
