package com.saasresearcher.learning.workspace.converter;

import com.saasresearcher.learning.workspace.api.WorkspaceState;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class WorkspaceStateConverter implements AttributeConverter<WorkspaceState, Short> {

    @Override
    public Short convertToDatabaseColumn(WorkspaceState state) {
        return state.code();
    }

    @Override
    public WorkspaceState convertToEntityAttribute(Short code) {
        return WorkspaceState.fromCode(code);
    }
}
