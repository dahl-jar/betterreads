package com.betterreads.collections.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.jspecify.annotations.Nullable;

/** Persists {@link ReadingStatus} as its lowercase {@code dbValue} rather than the enum name. */
@Converter(autoApply = true)
public class ReadingStatusConverter implements AttributeConverter<ReadingStatus, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(final @Nullable ReadingStatus status) {
        return status == null ? null : status.dbValue();
    }

    @Override
    public @Nullable ReadingStatus convertToEntityAttribute(final @Nullable String dbValue) {
        return dbValue == null ? null : ReadingStatus.fromDbValue(dbValue);
    }
}
