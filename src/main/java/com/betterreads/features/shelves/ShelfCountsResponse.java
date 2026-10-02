package com.betterreads.features.shelves;

record ShelfCountsResponse(long wantToRead, long currentlyReading, long finished, long dropped) {
}
