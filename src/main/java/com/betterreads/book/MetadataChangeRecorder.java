package com.betterreads.book;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class MetadataChangeRecorder {

    private final BookMetadataChangeRepository changes;

    MetadataChangeRecorder(final BookMetadataChangeRepository changes) {
        this.changes = changes;
    }

    void record(
        final long bookId,
        final Map<VerifiedField, @Nullable String> oldValues,
        final Book after,
        final Map<VerifiedField, FieldEvidence> evidence,
        final int checkVersion
    ) {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        final Map<VerifiedField, @Nullable String> newValues = BookSnapshot.of(after);
        changes.saveAll(oldValues.keySet().stream()
            .filter(field -> !Objects.equals(oldValues.get(field), newValues.get(field)))
            .map(field -> new BookMetadataChange(bookId,
                new BookMetadataChange.Change(field, oldValues.get(field), newValues.get(field), evidence.get(field)),
                checkVersion, now))
            .toList());
    }
}
