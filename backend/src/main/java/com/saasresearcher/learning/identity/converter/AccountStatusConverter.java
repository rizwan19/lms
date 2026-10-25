package com.saasresearcher.learning.identity.converter;

import com.saasresearcher.learning.identity.api.AccountStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AccountStatusConverter implements AttributeConverter<AccountStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(AccountStatus value) {
        return value.code();
    }

    @Override
    public AccountStatus convertToEntityAttribute(Short code) {
        return AccountStatus.fromCode(code);
    }
}
