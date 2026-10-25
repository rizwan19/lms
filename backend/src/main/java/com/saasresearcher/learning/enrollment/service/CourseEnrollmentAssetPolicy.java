package com.saasresearcher.learning.enrollment.service;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.courses.api.CourseCatalogService;
import com.saasresearcher.learning.enrollment.api.EnrollmentStatus;
import com.saasresearcher.learning.enrollment.repository.CourseEnrollmentRepository;
import com.saasresearcher.learning.media.api.AssetAccessPolicy;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lesson files are readable by learners whose active enrollment is pinned to an edition that uses them. Checked on
 * every request, so suspension and withdrawal take effect immediately for new requests.
 */
@Component
public class CourseEnrollmentAssetPolicy implements AssetAccessPolicy {

    private final CourseCatalogService catalog;
    private final CourseEnrollmentRepository enrollments;

    CourseEnrollmentAssetPolicy(CourseCatalogService catalog, CourseEnrollmentRepository enrollments) {
        this.catalog = catalog;
        this.enrollments = enrollments;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID assetId, Actor actor) {
        Set<UUID> versions = catalog.versionsUsingAsset(assetId);
        return !versions.isEmpty() && enrollments.existsByLearnerIdAndStatusAndCourseVersionIdIn(actor.userId(), EnrollmentStatus.ACTIVE, versions);
    }
}
