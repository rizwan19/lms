package com.saasresearcher.learning.assessments.controller;

import com.saasresearcher.learning.assessments.dto.StudentExamViews.StudentExam;
import com.saasresearcher.learning.assessments.service.StudentExamService;
import com.saasresearcher.learning.common.Actor;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** A student's exams for one of their enrollments, with their own attempts. No keys, no unpublished scores. */
@RestController
public class StudentExamController {

    private final StudentExamService exams;

    StudentExamController(StudentExamService exams) {
        this.exams = exams;
    }

    @GetMapping("/me/enrollments/{id}/exams")
    List<StudentExam> exams(@PathVariable UUID id, Actor actor) {
        return exams.exams(id, actor);
    }
}
