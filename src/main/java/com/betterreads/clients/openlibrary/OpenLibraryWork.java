package com.betterreads.clients.openlibrary;

import java.util.List;

import com.betterreads.booksource.NullableLists;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Work detail from OpenLibrary {@code /works/{key}.json}.
 *
 * <p>{@code description} is an {@code Object} because OpenLibrary sends it as a plain string on
 * some works and a {@code {"type", "value"}} object on others. {@code subjects} is large and mixes
 * real genres with machine tags.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenLibraryWork(
    @Nullable String title,
    @Nullable Object description,
    @Nullable List<String> subjects
) {

    public OpenLibraryWork {
        subjects = NullableLists.copyOf(subjects);
    }

    @Override
    @Nullable
    public List<String> subjects() {
        return NullableLists.copyOf(subjects);
    }
}
