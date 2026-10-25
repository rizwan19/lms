package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.ExamSchedule;
import com.saasresearcher.learning.assessments.dto.LearnerExamViews.AttemptSummary;
import com.saasresearcher.learning.assessments.dto.LearnerExamViews.LearnerExam;
import com.saasresearcher.learning.assessments.entity.ExamAttempt;
import com.saasresearcher.learning.assessments.repository.ExamRepository;
import com.saasresearcher.learning.assessments.repository.ExamAttemptRepository;
import com.saasresearcher.learning.assessments.service.ExamContentStore.Summary;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.enrollment.api.EnrollmentAccessService;
import com.saasresearcher.learning.enrollment.api.LearnerEnrollment;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LearnerExamService {

    private final EnrollmentAccessService enrollmentAccessService;
    private final ExamRepository examRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ExamContentStore examContentStore;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<LearnerExam> getExams(UUID enrollmentId, Actor actor) {
        LearnerEnrollment own = enrollmentAccessService.requireOwn(enrollmentId, actor);
        Instant now = clock.instant();
        return examRepository.findByCourseVersionIdOrderByCreatedAtAscIdAsc(own.enrollment().courseVersionId()).stream().map(exam -> {
            Summary summary = examContentStore.summary(exam.getId());
            List<ExamAttempt> mine = examAttemptRepository.findByEnrollmentIdAndExamIdOrderByAttemptNumber(enrollmentId, exam.getId());
            UUID active = mine.stream().filter(attempt -> attempt.getStatus() == AttemptStatus.IN_PROGRESS && !attempt.isExpired())
                    .map(ExamAttempt::getId).findFirst().orElse(null);
            boolean canStart = own.accessOpen() && exam.isOpen(now) && (active != null || mine.size() < exam.getMaxAttempts());
            ExamSchedule schedule = exam.schedule();
            return new LearnerExam(exam.getId(), exam.getTitle(), exam.isRequired(), schedule.mode(), summary.questionCount(), summary.maxPoints(),
                    schedule.opensAt(), schedule.closesAt(), schedule.durationMinutes(), schedule.maxAttempts(), mine.size(), active, canStart,
                    mine.stream().map(attempt -> new AttemptSummary(attempt.getId(), attempt.getAttemptNumber(), attempt.getStatus(), attempt.getFinalizedAt()))
                            .toList());
        }).toList();
    }
}
