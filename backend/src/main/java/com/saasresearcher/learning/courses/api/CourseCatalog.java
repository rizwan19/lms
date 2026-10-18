package com.saasresearcher.learning.courses.api;

import java.util.Set;
import java.util.UUID;

public interface CourseCatalog {

    /** Loads a published edition; drafts and unknown IDs are rejected as not found. */
    PublishedCourse requirePublished(UUID versionId);

    boolean isCourseTeacher(UUID courseId, UUID userId);

    /** Published or draft editions whose lessons use the asset as video or resource. */
    Set<UUID> versionsUsingAsset(UUID assetId);
}
