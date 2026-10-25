package com.saasresearcher.learning.courses.api;

import java.util.UUID;

public record CourseVersionInfo(UUID versionId, UUID courseId, Integer edition, boolean draft) {
}
