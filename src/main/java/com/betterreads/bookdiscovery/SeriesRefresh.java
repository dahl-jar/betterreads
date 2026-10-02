package com.betterreads.bookdiscovery;

@FunctionalInterface
public interface SeriesRefresh {

    int refresh(String seriesName);
}
