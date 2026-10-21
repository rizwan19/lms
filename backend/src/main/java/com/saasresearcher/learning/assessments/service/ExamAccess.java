package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.entity.ExamRows.ExamRow;
import com.saasresearcher.learning.assessments.repository.ExamRepository;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.courses.api.CourseCatalog;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Exam authoring follows course authorship: administrators and the course's teachers. */
@Component
public class ExamAccess {

    private final ExamRepository exams;
    private final CourseCatalog courses;

    ExamAccess(ExamRepository exams, CourseCatalog courses) {
        this.exams = exams;
        this.courses = courses;
    }

    void requireCourseAuthor(UUID courseId, Actor actor) {
        if (!actor.isAdministrator() && !courses.isCourseTeacher(courseId, actor.userId())) {
            throw ApiException.notFound();
        }
    }

    ExamRow requireExam(UUID examId, Actor actor) {
        ExamRow exam = exams.findExam(examId).orElseThrow(ApiException::notFound);
        requireCourseAuthor(exam.courseId(), actor);
        return exam;
    }
}
