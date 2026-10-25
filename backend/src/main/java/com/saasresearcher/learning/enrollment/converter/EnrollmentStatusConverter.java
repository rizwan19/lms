package com.saasresearcher.learning.enrollment.converter;

import com.saasresearcher.learning.enrollment.api.EnrollmentStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EnrollmentStatusConverter implements AttributeConverter<EnrollmentStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(EnrollmentStatus value) {
        return value.code();
    }

    @Override
    public EnrollmentStatus convertToEntityAttribute(Short code) {
        return EnrollmentStatus.fromCode(code);
    }
}
