package com.saasresearcher.learning.classrooms.service;

import com.saasresearcher.learning.classrooms.repository.ClassroomRepository;
import com.saasresearcher.learning.classrooms.repository.EnrollmentRepository;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.courses.api.CourseCatalog;
import com.saasresearcher.learning.media.api.AssetAccessPolicy;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lesson files of a class's course edition: readable by its class teachers, and by students with an active
 * enrollment while the class is open. Checked on every request, so withdrawal takes effect immediately.
 */
@Component
public class ClassAssetPolicy implements AssetAccessPolicy {

    private final CourseCatalog catalog;
    private final ClassroomRepository classes;
    private final EnrollmentRepository enrollments;
    private final Clock clock;

    ClassAssetPolicy(CourseCatalog catalog, ClassroomRepository classes, EnrollmentRepository enrollments, Clock clock) {
        this.catalog = catalog;
        this.classes = classes;
        this.enrollments = enrollments;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID assetId, Actor actor) {
        Set<UUID> versions = catalog.versionsUsingAsset(assetId);
        return !versions.isEmpty() && (classes.teachesAnyVersion(actor.userId(), versions)
                || enrollments.hasOpenEnrollmentForVersions(actor.userId(), versions, clock.instant()));
    }
}
