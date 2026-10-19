package com.saasresearcher.learning.assessments.internal;

import com.saasresearcher.learning.assessments.api.AnswerSaveCommand;
import com.saasresearcher.learning.assessments.api.AnswerSaved;
import com.saasresearcher.learning.assessments.api.AttemptOutcome;
import com.saasresearcher.learning.assessments.api.AttemptService;
import com.saasresearcher.learning.assessments.api.AttemptView;
import com.saasresearcher.learning.common.Actor;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** A student's own attempts. Responses never include keys or unpublished grades. */
@RestController
class AttemptController {

    private final AttemptService attempts;
    private final AnswerService answers;

    AttemptController(AttemptService attempts, AnswerService answers) {
        this.attempts = attempts;
        this.answers = answers;
    }

    @PostMapping("/exam-assignments/{id}/attempts")
    AttemptView start(@PathVariable UUID id, Actor actor) {
        return attempts.start(id, actor);
    }

    @GetMapping("/attempts/{id}")
    AttemptView get(@PathVariable UUID id, Actor actor) {
        return attempts.get(id, actor);
    }

    @PutMapping("/attempts/{id}/answers/{questionId}")
    AnswerSaved save(@PathVariable UUID id, @PathVariable UUID questionId, @RequestBody AnswerSaveCommand command, Actor actor) {
        return answers.save(id, questionId, command, actor);
    }

    @PostMapping("/attempts/{id}/submit")
    AttemptOutcome submit(@PathVariable UUID id, Actor actor) {
        return answers.submit(id, actor);
    }
}
