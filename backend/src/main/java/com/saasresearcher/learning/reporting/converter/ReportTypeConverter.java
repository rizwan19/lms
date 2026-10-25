package com.saasresearcher.learning.reporting.converter;

import com.saasresearcher.learning.reporting.api.ReportType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReportTypeConverter implements AttributeConverter<ReportType, Short> {

    @Override
    public Short convertToDatabaseColumn(ReportType value) {
        return value.code();
    }

    @Override
    public ReportType convertToEntityAttribute(Short code) {
        return ReportType.fromCode(code);
    }
}
