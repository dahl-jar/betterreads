package com.betterreads.catalog.service.source.model;

/**
 * One ordered volume of a series, with the book as Hardcover returns it.
 *
 * @param position 1-based position within the series
 * @param book the Hardcover-sourced book, carrying the fields the series query returned
 */
public record SourceSeriesVolume(int position, SourceBook book) {
}
