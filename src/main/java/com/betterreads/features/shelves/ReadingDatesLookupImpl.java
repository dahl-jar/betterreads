package com.betterreads.features.shelves;

import com.betterreads.shelfaccess.ReaderBook;
import com.betterreads.shelfaccess.ReadingDates;
import com.betterreads.shelfaccess.ReadingDatesLookup;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReadingDatesLookupImpl implements ReadingDatesLookup {

    private final ShelfEntryRepository entries;

    ReadingDatesLookupImpl(final ShelfEntryRepository entries) {
        this.entries = entries;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<ReaderBook, ReadingDates> datesFor(final Collection<ReaderBook> readerBooks) {
        if (readerBooks.isEmpty()) {
            return Map.of();
        }
        final Set<ReaderBook> wanted = Set.copyOf(readerBooks);
        final Set<Long> userIds = wanted.stream().map(ReaderBook::userId).collect(Collectors.toSet());
        final Set<Long> bookIds = wanted.stream().map(ReaderBook::bookId).collect(Collectors.toSet());
        return entries.findForActiveReaders(userIds, bookIds).stream()
            .filter(entry -> wanted.contains(readerBookOf(entry)))
            .collect(Collectors.toMap(
                ReadingDatesLookupImpl::readerBookOf,
                entry -> new ReadingDates(entry.getStartedAt(), entry.getFinishedAt())));
    }

    private static ReaderBook readerBookOf(final ShelfEntry entry) {
        return new ReaderBook(entry.getUserId(), entry.getBookId());
    }
}
