package com.betterreads.book;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class VerifiedFieldsConverter implements AttributeConverter<Set<VerifiedField>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(final Set<VerifiedField> fields) {
        return fields.stream().map(Enum::name).sorted().collect(Collectors.joining(SEPARATOR));
    }

    @Override
    public Set<VerifiedField> convertToEntityAttribute(final String column) {
        final Set<VerifiedField> fields = EnumSet.noneOf(VerifiedField.class);
        Arrays.stream(column.split(SEPARATOR))
            .filter(name -> !name.isBlank())
            .map(VerifiedField::valueOf)
            .forEach(fields::add);
        return fields;
    }
}
