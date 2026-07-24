package com.betterreads.catalog.service.source.model;

import java.util.List;

/**
 * A resolved series and its ordered volumes.
 *
 * <p>The volumes arrive ordered by position and collapsed to one per position, English editions
 * only, with boxed sets and positions past the series' primary count removed.
 */
public record SourceSeries(String name, String author, List<SourceSeriesVolume> volumes) {

    public SourceSeries {
        volumes = List.copyOf(volumes);
    }

    @Override
    public List<SourceSeriesVolume> volumes() {
        return List.copyOf(volumes);
    }
}
