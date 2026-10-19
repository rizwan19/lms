package com.saasresearcher.learning.courses.internal;

import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.courses.api.CourseCatalog;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.courses.internal.CourseRows.CourseRow;
import com.saasresearcher.learning.courses.internal.CourseRows.CourseStatus;
import com.saasresearcher.learning.courses.internal.CourseRows.VersionRow;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CourseCatalogService implements CourseCatalog {

    private final CourseRepository courses;
    private final CourseContentRepository content;

    CourseCatalogService(CourseRepository courses, CourseContentRepository content) {
        this.courses = courses;
        this.content = content;
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedCourse requirePublished(UUID versionId) {
        VersionRow version = courses.findVersion(versionId).filter(row -> !row.isDraft()).orElseThrow(ApiException::notFound);
        CourseRow course = courses.findCourse(version.courseId()).orElseThrow(ApiException::notFound);
        return new PublishedCourse(version.id(), version.courseId(), version.edition(), version.title(), version.description(),
                version.publishedAt(), course.status() == CourseStatus.ARCHIVED, content.readTree(version.id()),
                content.requiredExams(version.id()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCourseTeacher(UUID courseId, UUID userId) {
        return courses.isTeacher(courseId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> versionsUsingAsset(UUID assetId) {
        return content.versionsUsingAsset(assetId);
    }
}
