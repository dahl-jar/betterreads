package com.betterreads.book;

/** Published after a book is promoted into the catalog. */
public record BookPromotedEvent(String dedupKey) {
}
