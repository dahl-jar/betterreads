package com.betterreads.book;

import java.util.List;

import com.betterreads.booksource.NullableLists;
import org.jspecify.annotations.Nullable;

public record VerifiedMetadata(
    @Nullable String title,
    @Nullable List<String> authors,
    @Nullable Integer year,
    @Nullable String seriesName,
    @Nullable Integer seriesPosition,
    @Nullable String description,
    @Nullable String isbn13
) {

    public static final VerifiedMetadata NONE = new VerifiedMetadata(null, null, null, null, null, null, null);

    public VerifiedMetadata {
        authors = NullableLists.copyOf(authors);
    }

    @Override
    @Nullable
    public List<String> authors() {
        return NullableLists.copyOf(authors);
    }
}
