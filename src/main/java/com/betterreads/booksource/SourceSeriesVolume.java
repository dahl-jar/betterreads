package com.betterreads.booksource;

/** Series volume at a 1-based position, with the book as the Hardcover series query returns it. */
public record SourceSeriesVolume(int position, SourceBook book) {
}
