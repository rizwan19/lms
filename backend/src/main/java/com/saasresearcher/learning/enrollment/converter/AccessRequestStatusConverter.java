package com.saasresearcher.learning.enrollment.converter;

import com.saasresearcher.learning.enrollment.api.AccessRequestStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Nullable column.
@Converter(autoApply = true)
public class AccessRequestStatusConverter implements AttributeConverter<AccessRequestStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(AccessRequestStatus value) {
        return value == null ? null : value.code();
    }

    @Override
    public AccessRequestStatus convertToEntityAttribute(Short code) {
        return code == null ? null : AccessRequestStatus.fromCode(code);
    }
}
