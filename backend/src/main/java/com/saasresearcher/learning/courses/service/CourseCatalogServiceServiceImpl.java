package com.saasresearcher.learning.courses.service;

import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.courses.api.CourseCatalogService;
import com.saasresearcher.learning.courses.api.CourseStatus;
import com.saasresearcher.learning.courses.api.CourseVersionInfo;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.courses.entity.Course;
import com.saasresearcher.learning.courses.entity.CourseVersion;
import com.saasresearcher.learning.courses.api.EditionStatus;
import com.saasresearcher.learning.courses.repository.CourseRepository;
import com.saasresearcher.learning.courses.repository.CourseVersionRepository;
import com.saasresearcher.learning.courses.repository.LessonRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseCatalogServiceServiceImpl implements CourseCatalogService {

    private final CourseRepository courseRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final LessonRepository lessonRepository;
    private final CourseContent courseContent;

    @Override
    @Transactional(readOnly = true)
    public PublishedCourse requirePublished(UUID versionId) {
        return published(courseVersionRepository.findById(versionId).filter(version -> !version.isDraft()).orElseThrow(ApiException::notFound));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublishedCourse> latestPublished(UUID courseId) {
        return courseVersionRepository.findFirstByCourseIdAndStatusOrderByEditionDesc(courseId, EditionStatus.PUBLISHED).map(this::published);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseVersionInfo requireVersion(UUID versionId) {
        CourseVersion version = courseVersionRepository.findById(versionId).orElseThrow(ApiException::notFound);
        return new CourseVersionInfo(version.getId(), version.getCourseId(), version.getEdition(), version.isDraft());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseVersionInfo> draftOf(UUID courseId) {
        return courseVersionRepository.findByCourseIdAndStatus(courseId, EditionStatus.DRAFT)
                .map(version -> new CourseVersionInfo(version.getId(), version.getCourseId(), null, true));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> versionsUsingAsset(UUID assetId) {
        return lessonRepository.versionsUsingAsset(assetId);
    }

    private PublishedCourse published(CourseVersion version) {
        Course course = courseRepository.findById(version.getCourseId()).orElseThrow(ApiException::notFound);
        return new PublishedCourse(version.getId(), version.getCourseId(), course.getAuthorId(), version.getEdition(), version.getTitle(), version.getDescription(),
                version.getPublishedAt(), course.getStatus() == CourseStatus.ARCHIVED, courseContent.readTree(version.getId()));
    }
}
