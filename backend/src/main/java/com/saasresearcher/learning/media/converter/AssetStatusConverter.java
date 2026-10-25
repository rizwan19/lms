package com.saasresearcher.learning.media.converter;

import com.saasresearcher.learning.media.api.AssetStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AssetStatusConverter implements AttributeConverter<AssetStatus, Short> {

    @Override
    public Short convertToDatabaseColumn(AssetStatus value) {
        return value.code();
    }

    @Override
    public AssetStatus convertToEntityAttribute(Short code) {
        return AssetStatus.fromCode(code);
    }
}
