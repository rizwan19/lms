package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.entity.Exam;
import com.saasresearcher.learning.assessments.repository.ExamRepository;
import com.saasresearcher.learning.common.ErrorCodes;
import com.saasresearcher.learning.courses.api.CourseEditionListener;
import com.saasresearcher.learning.enrollment.api.GrantPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExamEditionPolicy implements CourseEditionListener, GrantPolicy {

    private final ExamRepository examRepository;
    private final ExamContentStore examContentStore;
    private final Clock clock;

    @Override
    public void beforePublish(UUID courseId, UUID draftVersionId) {
        List<Exam> drafted = examRepository.findByCourseVersionIdOrderByCreatedAtAscIdAsc(draftVersionId);
        for (int e = 0; e < drafted.size(); e++) {
            Exam exam = drafted.get(e);
            ExamRules.requirePublishable("exams[" + e + "]", exam.getTitle(), examContentStore.getQuestionContents(exam.getId()));
            ScheduleRules.require(exam.schedule(), examContentStore.summary(exam.getId()).mixed(), exam.isRequired());
        }
    }

    @Override
    public void draftStarted(UUID courseId, UUID fromVersionId, UUID draftVersionId) {
        Instant now = clock.instant();
        for (Exam source : examRepository.findByCourseVersionIdOrderByCreatedAtAscIdAsc(fromVersionId)) {
            Exam copy = examRepository.saveAndFlush(source.copyTo(draftVersionId, now));
            examContentStore.copy(source.getId(), copy.getId());
        }
    }

    @Override
    public Optional<Blocker> blocker(UUID courseVersionId) {
        Instant now = clock.instant();
        return examRepository.findByCourseVersionIdOrderByCreatedAtAscIdAsc(courseVersionId).stream()
                .filter(exam -> exam.isRequired() && exam.hasClosed(now))
                .findFirst()
                .map(closed -> new Blocker(ErrorCodes.REQUIRED_EXAM_CLOSED,
                        "A required exam of this edition has closed; the author must extend it or publish a new edition"));
    }
}
