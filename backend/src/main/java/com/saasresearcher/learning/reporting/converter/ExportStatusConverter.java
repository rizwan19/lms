package com.saasresearcher.learning.reporting.converter;

import com.saasresearcher.learning.reporting.api.ExportStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ExportStatusConverter implements AttributeConverter<ExportStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(ExportStatus value) {
        return value.code();
    }

    @Override
    public ExportStatus convertToEntityAttribute(Short code) {
        return ExportStatus.fromCode(code);
    }
}
