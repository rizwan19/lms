package com.saasresearcher.learning.assessments.converter;

import com.saasresearcher.learning.assessments.api.ResultPolicy;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ResultPolicyConverter implements AttributeConverter<ResultPolicy, Short> {

    @Override
    public Short convertToDatabaseColumn(ResultPolicy value) {
        return value.code();
    }

    @Override
    public ResultPolicy convertToEntityAttribute(Short code) {
        return ResultPolicy.fromCode(code);
    }
}
