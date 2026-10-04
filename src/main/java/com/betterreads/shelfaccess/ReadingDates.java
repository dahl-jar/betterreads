package com.betterreads.shelfaccess;

import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

public record ReadingDates(@Nullable LocalDate startedAt, @Nullable LocalDate finishedAt) {

    public static final ReadingDates NONE = new ReadingDates(null, null);
}
