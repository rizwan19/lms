package com.saasresearcher.learning.certificates.converter;

import com.saasresearcher.learning.certificates.api.CertificateStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CertificateStatusConverter implements AttributeConverter<CertificateStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(CertificateStatus value) {
        return value.code();
    }

    @Override
    public CertificateStatus convertToEntityAttribute(Short code) {
        return CertificateStatus.fromCode(code);
    }
}
