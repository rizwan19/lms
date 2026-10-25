package com.saasresearcher.learning.assessments.controller;

import com.saasresearcher.learning.assessments.dto.ExamDraftRequest;
import com.saasresearcher.learning.assessments.dto.ExamScheduleRequest;
import com.saasresearcher.learning.assessments.dto.ExamViews.ExamContent;
import com.saasresearcher.learning.assessments.dto.ExamViews.ExamView;
import com.saasresearcher.learning.assessments.dto.ExamRequest;
import com.saasresearcher.learning.assessments.service.ExamAuthoringService;
import com.saasresearcher.learning.common.Actor;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExamController {

    private final ExamAuthoringService examAuthoringService;

    @GetMapping("/courses/{id}/exams")
    List<ExamView> list(@PathVariable UUID id, @RequestParam(required = false) UUID versionId, Actor actor) {
        return examAuthoringService.list(id, versionId, actor);
    }

    @PostMapping("/courses/{id}/exams")
    @ResponseStatus(HttpStatus.CREATED)
    ExamView createExam(@PathVariable UUID id, @Valid @RequestBody ExamRequest request, Actor actor) {
        return examAuthoringService.createExam(id, request.title(), request.required(), request.schedule(), actor);
    }

    @GetMapping("/exams/{id}")
    ExamContent getExam(@PathVariable UUID id, Actor actor) {
        return examAuthoringService.getExam(id, actor);
    }

    @PutMapping("/exams/{id}")
    ExamContent saveExam(@PathVariable UUID id, @RequestBody ExamDraftRequest request, Actor actor) {
        return examAuthoringService.saveExam(id, request, actor);
    }

    @PutMapping("/exams/{id}/schedule")
    ExamView schedule(@PathVariable UUID id, @RequestBody ExamScheduleRequest request, Actor actor) {
        return examAuthoringService.updateSchedule(id, request.expectedRevision(), request.required(), request.schedule(), actor);
    }

    @DeleteMapping("/exams/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteExam(@PathVariable UUID id, Actor actor) {
        examAuthoringService.deleteExam(id, actor);
    }
}
