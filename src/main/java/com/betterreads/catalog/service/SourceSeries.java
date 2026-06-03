package com.betterreads.catalog.service;

import java.util.List;

/**
 * A resolved series and its ordered volumes.
 *
 * <p>The volumes arrive collapsed to one per position, English editions only, with boxed sets and
 * positions past the series' primary count removed.
 *
 * @param name series name
 * @param author series author display name
 * @param volumes volumes ordered by position
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
