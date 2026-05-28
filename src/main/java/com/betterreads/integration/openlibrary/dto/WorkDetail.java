package com.betterreads.integration.openlibrary.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Work detail from OpenLibrary {@code /works/{key}.json}.
 *
 * <p>{@code description} is an {@code Object} because OpenLibrary sends it as a plain string on
 * some works and a {@code {"type", "value"}} object on others; the mapper handles both.
 * {@code subjects} is large and mixes real genres with machine tags the mapper drops.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkDetail(
    @Nullable String key,
    @Nullable String title,
    @Nullable Object description,
    @Nullable List<String> subjects
) {

    public WorkDetail {
        if (subjects != null) {
            subjects = List.copyOf(subjects);
        }
    }

    @Override
    @Nullable
    public List<String> subjects() {
        return subjects == null ? null : List.copyOf(subjects);
    }
}
