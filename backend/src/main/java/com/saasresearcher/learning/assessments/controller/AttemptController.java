package com.saasresearcher.learning.assessments.controller;

import com.saasresearcher.learning.assessments.api.AnswerSaveRequest;
import com.saasresearcher.learning.assessments.api.AnswerSaved;
import com.saasresearcher.learning.assessments.api.AttemptOutcome;
import com.saasresearcher.learning.assessments.api.AttemptService;
import com.saasresearcher.learning.assessments.api.AttemptView;
import com.saasresearcher.learning.assessments.service.AnswerService;
import com.saasresearcher.learning.common.Actor;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;
    private final AnswerService answerService;

    @PostMapping("/exams/{id}/attempts")
    AttemptView start(@PathVariable UUID id, Actor actor) {
        return attemptService.startAttempt(id, actor);
    }

    @GetMapping("/attempts/{id}")
    AttemptView getAttempt(@PathVariable UUID id, Actor actor) {
        return attemptService.get(id, actor);
    }

    @PutMapping("/attempts/{id}/answers/{questionId}")
    AnswerSaved saveAnswer(@PathVariable UUID id, @PathVariable UUID questionId, @RequestBody AnswerSaveRequest request, Actor actor) {
        return answerService.saveAnswer(id, questionId, request, actor);
    }

    @PostMapping("/attempts/{id}/submit")
    AttemptOutcome submit(@PathVariable UUID id, Actor actor) {
        return answerService.submit(id, actor);
    }
}
