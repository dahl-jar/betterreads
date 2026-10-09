package com.betterreads.booksource;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record SourceSeries(
    String name,
    String author,
    List<SourceSeriesVolume> volumes,
    @Nullable SourceBook titleBook
) {

    public SourceSeries {
        volumes = List.copyOf(volumes);
    }
}
