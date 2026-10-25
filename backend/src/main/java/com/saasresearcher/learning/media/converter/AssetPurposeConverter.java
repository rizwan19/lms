package com.saasresearcher.learning.media.converter;

import com.saasresearcher.learning.media.api.AssetPurpose;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AssetPurposeConverter implements AttributeConverter<AssetPurpose, Short> {

    @Override
    public Short convertToDatabaseColumn(AssetPurpose value) {
        return value.code();
    }

    @Override
    public AssetPurpose convertToEntityAttribute(Short code) {
        return AssetPurpose.fromCode(code);
    }
}
