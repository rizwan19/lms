package com.saasresearcher.learning.grading.converter;

import com.saasresearcher.learning.grading.api.RevisionStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RevisionStatusConverter implements AttributeConverter<RevisionStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(RevisionStatus value) {
        return value.code();
    }

    @Override
    public RevisionStatus convertToEntityAttribute(Short code) {
        return RevisionStatus.fromCode(code);
    }
}
