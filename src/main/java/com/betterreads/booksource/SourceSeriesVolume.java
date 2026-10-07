package com.betterreads.booksource;

/** Series volume at its Hardcover position, with the book as the Hardcover series query returns it. */
public record SourceSeriesVolume(double position, SourceBook book) {
}
