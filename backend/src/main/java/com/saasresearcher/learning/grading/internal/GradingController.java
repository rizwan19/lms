package com.saasresearcher.learning.grading.internal;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.grading.api.PublishedResult;
import com.saasresearcher.learning.grading.internal.GradeRows.GradeRow;
import com.saasresearcher.learning.grading.internal.GradeRows.RevisionRow;
import com.saasresearcher.learning.grading.internal.GradingQueue.Filter;
import com.saasresearcher.learning.grading.internal.GradingQueue.QueueRow;
import com.saasresearcher.learning.grading.internal.GradingService.GradeCommand;
import com.saasresearcher.learning.grading.internal.GradingService.GradeView;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Teacher grading of their classes' attempts. Responses include keys and are staff-only. */
@RestController
@RequestMapping("/grading")
class GradingController {

    private final GradingService grading;
    private final GradingQueue queue;

    GradingController(GradingService grading, GradingQueue queue) {
        this.grading = grading;
        this.queue = queue;
    }

    @GetMapping
    PageResponse<QueueRow> queue(@RequestParam UUID classId, @RequestParam(defaultValue = "PENDING") Filter status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        return queue.list(classId, status, PageQuery.of(page, size), actor);
    }

    @GetMapping("/attempts/{id}")
    GradeView view(@PathVariable UUID id, Actor actor) {
        return grading.view(id, actor);
    }

    @PutMapping("/attempts/{id}/answers/{questionId}")
    GradeRow grade(@PathVariable UUID id, @PathVariable UUID questionId, @RequestBody GradeCommand command, Actor actor) {
        return grading.saveGrade(id, questionId, command, actor);
    }

    @PostMapping("/attempts/{id}/finalize")
    RevisionRow finalizeGrade(@PathVariable UUID id, @RequestBody Revision request, Actor actor) {
        return grading.finalizeGrade(id, request.expectedRevision(), actor);
    }

    @PostMapping("/attempts/{id}/publish")
    PublishedResult publish(@PathVariable UUID id, @RequestBody Revision request, Actor actor) {
        return grading.publish(id, request.expectedRevision(), actor);
    }

    @PostMapping("/attempts/{id}/regrade")
    RevisionRow regrade(@PathVariable UUID id, @RequestBody Regrade request, Actor actor) {
        return grading.regrade(id, request.reason(), actor);
    }

    record Revision(long expectedRevision) {
    }

    record Regrade(String reason) {
    }
}
