package com.saasresearcher.learning.enrollment.converter;

import com.saasresearcher.learning.enrollment.api.GrantSource;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GrantSourceConverter implements AttributeConverter<GrantSource, Short> {

    @Override
    public Short convertToDatabaseColumn(GrantSource value) {
        return value.code();
    }

    @Override
    public GrantSource convertToEntityAttribute(Short code) {
        return GrantSource.fromCode(code);
    }
}
