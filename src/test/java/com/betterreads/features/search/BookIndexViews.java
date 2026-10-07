package com.betterreads.features.search;

import com.betterreads.bookindex.BookIndexView;
import java.util.List;

final class BookIndexViews {

    private BookIndexViews() {
    }

    static BookIndexView titled(final String dedupKey, final String title) {
        return new BookIndexView(dedupKey, title, null, null, null, List.of(), List.of(), List.of(), "en",
            null, null, null, null);
    }
}
