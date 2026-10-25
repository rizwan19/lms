package com.saasresearcher.learning.identity.converter;

import com.saasresearcher.learning.common.TeacherStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Nullable column.
@Converter(autoApply = true)
public class TeacherStatusConverter implements AttributeConverter<TeacherStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(TeacherStatus value) {
        return value == null ? null : value.code();
    }

    @Override
    public TeacherStatus convertToEntityAttribute(Short code) {
        return code == null ? null : TeacherStatus.fromCode(code);
    }
}
