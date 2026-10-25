package com.saasresearcher.learning.courses.converter;

import com.saasresearcher.learning.courses.api.EditionStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EditionStatusConverter implements AttributeConverter<EditionStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(EditionStatus status) {
        return status.code();
    }

    @Override
    public EditionStatus convertToEntityAttribute(Short code) {
        return EditionStatus.fromCode(code);
    }
}
