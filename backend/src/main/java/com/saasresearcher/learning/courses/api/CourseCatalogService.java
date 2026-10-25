package com.saasresearcher.learning.courses.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CourseCatalogService {

    PublishedCourse requirePublished(UUID versionId);

    Optional<PublishedCourse> latestPublished(UUID courseId);

    CourseVersionInfo requireVersion(UUID versionId);

    Optional<CourseVersionInfo> draftOf(UUID courseId);

    Set<UUID> versionsUsingAsset(UUID assetId);
}
