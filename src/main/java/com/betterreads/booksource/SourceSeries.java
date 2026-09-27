package com.betterreads.booksource;

import java.util.List;

/**
 * Series with its volumes ordered by position, one English edition per position, with boxed sets
 * and positions past the primary count removed.
 */
public record SourceSeries(String name, String author, List<SourceSeriesVolume> volumes) {

    public SourceSeries {
        volumes = List.copyOf(volumes);
    }
}
