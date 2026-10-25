package com.saasresearcher.learning.assessments.converter;

import com.saasresearcher.learning.assessments.api.ExamMode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ExamModeConverter implements AttributeConverter<ExamMode, Short> {

    @Override
    public Short convertToDatabaseColumn(ExamMode value) {
        return value.code();
    }

    @Override
    public ExamMode convertToEntityAttribute(Short code) {
        return ExamMode.fromCode(code);
    }
}
