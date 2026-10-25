package com.saasresearcher.learning.identity.converter;

import com.saasresearcher.learning.identity.api.TokenPurpose;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TokenPurposeConverter implements AttributeConverter<TokenPurpose, Short> {

    @Override
    public Short convertToDatabaseColumn(TokenPurpose value) {
        return value.code();
    }

    @Override
    public TokenPurpose convertToEntityAttribute(Short code) {
        return TokenPurpose.fromCode(code);
    }
}
