package com.saasresearcher.learning.courses.dto;

import com.saasresearcher.learning.courses.api.VideoProvider;
import java.util.List;
import java.util.UUID;

/** Client-supplied draft tree. IDs are optional and honoured only when they already belong to the draft. */
public record DraftInput(long expectedRevision, String title, String description, List<SectionInput> sections, List<UUID> requiredExamVersionIds) {

    public record SectionInput(UUID id, String title, List<LessonInput> lessons) {
    }

    public record LessonInput(UUID id, String title, String bodyHtml, UUID videoAssetId, VideoProvider videoProvider, String videoRef,
            List<UUID> resourceAssetIds, boolean required) {
    }
}
