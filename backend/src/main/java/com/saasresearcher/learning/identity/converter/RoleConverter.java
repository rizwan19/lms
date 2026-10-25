package com.saasresearcher.learning.identity.converter;

import com.saasresearcher.learning.common.Role;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RoleConverter implements AttributeConverter<Role, Short> {

    @Override
    public Short convertToDatabaseColumn(Role value) {
        return value.code();
    }

    @Override
    public Role convertToEntityAttribute(Short code) {
        return Role.fromCode(code);
    }
}
