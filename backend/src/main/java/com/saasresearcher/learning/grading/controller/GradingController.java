package com.saasresearcher.learning.grading.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.grading.api.PublishedResult;
import com.saasresearcher.learning.grading.dto.GradeViews.GradeCommand;
import com.saasresearcher.learning.grading.dto.GradeViews.GradeRow;
import com.saasresearcher.learning.grading.dto.GradeViews.GradeView;
import com.saasresearcher.learning.grading.dto.GradeViews.QueueView;
import com.saasresearcher.learning.grading.dto.GradeViews.RevisionView;
import com.saasresearcher.learning.grading.service.GradingQueue;
import com.saasresearcher.learning.grading.service.GradingQueue.Filter;
import com.saasresearcher.learning.grading.service.GradingService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Grading by the course's author. Responses include keys and private grades. */
@RestController
public class GradingController {

    private final GradingService grading;
    private final GradingQueue queue;

    GradingController(GradingService grading, GradingQueue queue) {
        this.grading = grading;
        this.queue = queue;
    }

    @GetMapping("/courses/{id}/grading")
    PageResponse<QueueView> queue(@PathVariable UUID id, @RequestParam(required = false) UUID versionId,
            @RequestParam(defaultValue = "PENDING") Filter status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, Actor actor) {
        return queue.list(id, versionId, status, PageQuery.of(page, size), actor);
    }

    @GetMapping("/grading/attempts/{id}")
    GradeView view(@PathVariable UUID id, Actor actor) {
        return grading.view(id, actor);
    }

    @PutMapping("/grading/attempts/{id}/answers/{questionId}")
    GradeRow grade(@PathVariable UUID id, @PathVariable UUID questionId, @RequestBody GradeCommand command, Actor actor) {
        return grading.saveGrade(id, questionId, command, actor);
    }

    @PostMapping("/grading/attempts/{id}/finalize")
    RevisionView finalizeGrade(@PathVariable UUID id, @RequestBody Revision request, Actor actor) {
        return grading.finalizeGrade(id, request.expectedRevision(), actor);
    }

    @PostMapping("/grading/attempts/{id}/publish")
    PublishedResult publish(@PathVariable UUID id, @RequestBody Revision request, Actor actor) {
        return grading.publish(id, request.expectedRevision(), actor);
    }

    @PostMapping("/grading/attempts/{id}/regrade")
    RevisionView regrade(@PathVariable UUID id, @RequestBody Regrade request, Actor actor) {
        return grading.regrade(id, request.reason(), actor);
    }

    record Revision(long expectedRevision) {
    }

    record Regrade(String reason) {
    }
}
