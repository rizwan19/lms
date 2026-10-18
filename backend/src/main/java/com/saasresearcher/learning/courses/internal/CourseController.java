package com.saasresearcher.learning.courses.internal;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.courses.api.PublishedCourse;
import com.saasresearcher.learning.courses.internal.CourseRows.CourseStatus;
import com.saasresearcher.learning.courses.internal.CourseRows.CourseSummary;
import com.saasresearcher.learning.courses.internal.CourseViews.CourseDetail;
import com.saasresearcher.learning.courses.internal.CourseViews.DraftView;
import com.saasresearcher.learning.courses.internal.CourseViews.PreviewView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/courses")
class CourseController {

    private final CourseAuthoringService authoring;
    private final CoursePublicationService publication;

    CourseController(CourseAuthoringService authoring, CoursePublicationService publication) {
        this.authoring = authoring;
        this.publication = publication;
    }

    @GetMapping
    PageResponse<CourseSummary> list(@RequestParam(defaultValue = "ACTIVE") CourseStatus status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, Actor actor) {
        return authoring.list(status, PageQuery.of(page, size), actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CourseDetail create(@Valid @RequestBody NewCourse request, Actor actor) {
        return authoring.detail(authoring.create(request.title(), actor), actor);
    }

    @GetMapping("/{id}")
    CourseDetail detail(@PathVariable UUID id, Actor actor) {
        return authoring.detail(id, actor);
    }

    @GetMapping("/{id}/draft")
    DraftView draft(@PathVariable UUID id, Actor actor) {
        return authoring.draft(id, actor);
    }

    @PutMapping("/{id}/draft")
    DraftView saveDraft(@PathVariable UUID id, @RequestBody DraftInput request, Actor actor) {
        return authoring.saveDraft(id, request, actor);
    }

    /** Starts a correction draft copied from a published edition. */
    @PostMapping("/{id}/draft")
    @ResponseStatus(HttpStatus.CREATED)
    DraftView startDraft(@PathVariable UUID id, @Valid @RequestBody StartDraft request, Actor actor) {
        return authoring.startDraft(id, request.fromVersionId(), actor);
    }

    @PostMapping("/{id}/publish")
    PublishedCourse publish(@PathVariable UUID id, @RequestBody Publish request, Actor actor) {
        return publication.publish(id, request.expectedRevision(), actor);
    }

    @GetMapping("/{id}/preview")
    PreviewView preview(@PathVariable UUID id, @RequestParam(required = false) UUID versionId, Actor actor) {
        return authoring.preview(id, versionId, actor);
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@PathVariable UUID id, Actor actor) {
        authoring.setStatus(id, CourseStatus.ARCHIVED, actor);
    }

    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restore(@PathVariable UUID id, Actor actor) {
        authoring.setStatus(id, CourseStatus.ACTIVE, actor);
    }

    @PutMapping("/{id}/teachers")
    CourseDetail setTeachers(@PathVariable UUID id, @Valid @RequestBody Teachers request, Actor actor) {
        authoring.setTeachers(id, request.teacherIds(), actor);
        return authoring.detail(id, actor);
    }

    record NewCourse(@NotNull String title) {
    }

    record StartDraft(@NotNull UUID fromVersionId) {
    }

    record Publish(long expectedRevision) {
    }

    record Teachers(@NotNull Set<UUID> teacherIds) {
    }
}
