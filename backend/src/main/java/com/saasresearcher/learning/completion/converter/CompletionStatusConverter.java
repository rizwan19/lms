package com.saasresearcher.learning.completion.converter;

import com.saasresearcher.learning.completion.api.CompletionStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CompletionStatusConverter implements AttributeConverter<CompletionStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(CompletionStatus value) {
        return value.code();
    }

    @Override
    public CompletionStatus convertToEntityAttribute(Short code) {
        return CompletionStatus.fromCode(code);
    }
}
