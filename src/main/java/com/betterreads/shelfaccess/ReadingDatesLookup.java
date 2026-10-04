package com.betterreads.shelfaccess;

import java.util.Collection;
import java.util.Map;

@FunctionalInterface
public interface ReadingDatesLookup {

    Map<ReaderBook, ReadingDates> datesFor(Collection<ReaderBook> readerBooks);
}
