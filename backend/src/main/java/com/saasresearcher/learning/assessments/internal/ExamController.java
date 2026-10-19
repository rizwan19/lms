package com.saasresearcher.learning.assessments.internal;

import com.saasresearcher.learning.assessments.internal.ExamRows.ExamSummary;
import com.saasresearcher.learning.assessments.internal.ExamViews.ExamContent;
import com.saasresearcher.learning.assessments.internal.ExamViews.ExamDetail;
import com.saasresearcher.learning.common.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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

/** Author endpoints; responses include answer keys and are restricted to course authors. */
@RestController
@RequestMapping("/exams")
class ExamController {

    private final ExamAuthoringService authoring;

    ExamController(ExamAuthoringService authoring) {
        this.authoring = authoring;
    }

    @GetMapping
    List<ExamSummary> list(@RequestParam UUID courseId, Actor actor) {
        return authoring.list(courseId, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ExamDetail create(@Valid @RequestBody NewExam request, Actor actor) {
        return authoring.detail(authoring.create(request.courseId(), request.title(), actor), actor);
    }

    @GetMapping("/{id}")
    ExamDetail detail(@PathVariable UUID id, Actor actor) {
        return authoring.detail(id, actor);
    }

    @GetMapping("/{id}/draft")
    ExamContent draft(@PathVariable UUID id, Actor actor) {
        return authoring.draft(id, actor);
    }

    @PutMapping("/{id}/draft")
    ExamContent saveDraft(@PathVariable UUID id, @RequestBody ExamDraftInput request, Actor actor) {
        return authoring.saveDraft(id, request, actor);
    }

    @PostMapping("/{id}/draft")
    @ResponseStatus(HttpStatus.CREATED)
    ExamContent startDraft(@PathVariable UUID id, @Valid @RequestBody StartDraft request, Actor actor) {
        return authoring.startDraft(id, request.fromVersionId(), actor);
    }

    @PostMapping("/{id}/publish")
    ExamContent publish(@PathVariable UUID id, @RequestBody Publish request, Actor actor) {
        return authoring.publish(id, request.expectedRevision(), actor);
    }

    @GetMapping("/{id}/editions/{versionId}")
    ExamContent edition(@PathVariable UUID id, @PathVariable UUID versionId, Actor actor) {
        return authoring.preview(id, versionId, actor);
    }

    record NewExam(@NotNull UUID courseId, @NotNull String title) {
    }

    record StartDraft(@NotNull UUID fromVersionId) {
    }

    record Publish(long expectedRevision) {
    }
}
