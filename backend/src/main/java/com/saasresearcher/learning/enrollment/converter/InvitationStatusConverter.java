package com.saasresearcher.learning.enrollment.converter;

import com.saasresearcher.learning.enrollment.api.InvitationStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class InvitationStatusConverter implements AttributeConverter<InvitationStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(InvitationStatus value) {
        return value.code();
    }

    @Override
    public InvitationStatus convertToEntityAttribute(Short code) {
        return InvitationStatus.fromCode(code);
    }
}
