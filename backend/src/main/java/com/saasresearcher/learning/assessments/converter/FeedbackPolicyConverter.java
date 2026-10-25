package com.saasresearcher.learning.assessments.converter;

import com.saasresearcher.learning.assessments.api.FeedbackPolicy;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class FeedbackPolicyConverter implements AttributeConverter<FeedbackPolicy, Short> {

    @Override
    public Short convertToDatabaseColumn(FeedbackPolicy value) {
        return value.code();
    }

    @Override
    public FeedbackPolicy convertToEntityAttribute(Short code) {
        return FeedbackPolicy.fromCode(code);
    }
}
