package com.saasresearcher.learning.completion.controller;

import com.saasresearcher.learning.classrooms.api.EnrollmentAccess;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.completion.api.CompletionState;
import com.saasresearcher.learning.completion.service.CompletionReconciler;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** A student's own completion requirements, including exams still waiting for published grades. */
@RestController
public class CompletionController {

    private final CompletionReconciler completions;
    private final EnrollmentAccess enrollments;

    CompletionController(CompletionReconciler completions, EnrollmentAccess enrollments) {
        this.completions = completions;
        this.enrollments = enrollments;
    }

    @GetMapping("/me/enrollments/{id}/completion")
    CompletionState completion(@PathVariable UUID id, Actor actor) {
        enrollments.requireOwn(id, actor);
        return completions.state(id);
    }
}
