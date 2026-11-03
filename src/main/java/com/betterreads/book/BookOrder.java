package com.betterreads.book;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BookOrder {

    private BookOrder() {
    }

    public static List<Book> byIds(final List<Long> ids, final Collection<Book> books) {
        final Map<Long, Book> byId = books.stream()
            .collect(Collectors.toMap(Book::getBookId, Function.identity()));
        return ids.stream().filter(byId::containsKey).map(byId::get).toList();
    }
}
