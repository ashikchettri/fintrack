package com.fintrack.finance.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link Visibility} as lowercase text ({@code personal}/{@code shared})
 * so the value matches the {@code ck_transactions_visibility} CHECK constraint
 * and reads naturally in raw SQL.
 */
@Converter(autoApply = true)
public class VisibilityConverter implements AttributeConverter<Visibility, String> {

    @Override
    public String convertToDatabaseColumn(Visibility attribute) {
        return attribute == null ? null : attribute.dbValue();
    }

    @Override
    public Visibility convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Visibility.fromDb(dbData);
    }
}
