package com.betterreads.catalog.service;

/**
 * One ordered volume of a series, with the book as Hardcover already knows it.
 *
 * @param position 1-based position within the series
 * @param book the Hardcover-sourced book, carrying the fields the enumeration returned
 */
public record SourceSeriesVolume(int position, SourceBook book) {
}
