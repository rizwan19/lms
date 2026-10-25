package com.saasresearcher.learning.assessments.converter;

import com.saasresearcher.learning.assessments.api.QuestionType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class QuestionTypeConverter implements AttributeConverter<QuestionType, Short> {

    @Override
    public Short convertToDatabaseColumn(QuestionType type) {
        return type.code();
    }

    @Override
    public QuestionType convertToEntityAttribute(Short code) {
        return QuestionType.fromCode(code);
    }
}
