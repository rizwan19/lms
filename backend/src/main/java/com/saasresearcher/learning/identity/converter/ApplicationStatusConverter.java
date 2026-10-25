package com.saasresearcher.learning.identity.converter;

import com.saasresearcher.learning.identity.api.ApplicationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ApplicationStatusConverter implements AttributeConverter<ApplicationStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(ApplicationStatus value) {
        return value.code();
    }

    @Override
    public ApplicationStatus convertToEntityAttribute(Short code) {
        return ApplicationStatus.fromCode(code);
    }
}
