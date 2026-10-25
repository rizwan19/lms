package com.saasresearcher.learning.assessments.controller;

import com.saasresearcher.learning.assessments.dto.LearnerExamViews.LearnerExam;
import com.saasresearcher.learning.assessments.service.LearnerExamService;
import com.saasresearcher.learning.common.Actor;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LearnerExamController {

    private final LearnerExamService learnerExamService;

    @GetMapping("/me/enrollments/{id}/exams")
    List<LearnerExam> getLearnerExamService(@PathVariable UUID id, Actor actor) {
        return learnerExamService.getExams(id, actor);
    }
}
