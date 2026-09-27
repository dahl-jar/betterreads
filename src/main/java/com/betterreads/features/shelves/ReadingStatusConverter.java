package com.betterreads.features.shelves;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.jspecify.annotations.Nullable;

/** Stores a reading status as its lowercase column value. */
@Converter(autoApply = true)
class ReadingStatusConverter implements AttributeConverter<ReadingStatus, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(final @Nullable ReadingStatus status) {
        return status == null ? null : status.dbValue();
    }

    @Override
    public @Nullable ReadingStatus convertToEntityAttribute(final @Nullable String dbValue) {
        return dbValue == null ? null : ReadingStatus.fromDbValue(dbValue);
    }
}
