package com.saasresearcher.learning.courses.converter;

import com.saasresearcher.learning.courses.api.CourseStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CourseStatusConverter implements AttributeConverter<CourseStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(CourseStatus value) {
        return value.code();
    }

    @Override
    public CourseStatus convertToEntityAttribute(Short code) {
        return CourseStatus.fromCode(code);
    }
}
