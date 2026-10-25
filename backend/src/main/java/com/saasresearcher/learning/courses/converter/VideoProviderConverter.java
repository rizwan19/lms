package com.saasresearcher.learning.courses.converter;

import com.saasresearcher.learning.courses.api.VideoProvider;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Nullable column.
@Converter(autoApply = true)
public class VideoProviderConverter implements AttributeConverter<VideoProvider, Short> {

    @Override
    public Short convertToDatabaseColumn(VideoProvider value) {
        return value == null ? null : value.code();
    }

    @Override
    public VideoProvider convertToEntityAttribute(Short code) {
        return code == null ? null : VideoProvider.fromCode(code);
    }
}
