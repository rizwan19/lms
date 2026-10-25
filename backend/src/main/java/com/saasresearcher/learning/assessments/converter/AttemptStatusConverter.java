package com.saasresearcher.learning.assessments.converter;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AttemptStatusConverter implements AttributeConverter<AttemptStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(AttemptStatus status) {
        return status.code();
    }

    @Override
    public AttemptStatus convertToEntityAttribute(Short code) {
        return AttemptStatus.fromCode(code);
    }
}
