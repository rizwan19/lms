package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.api.ExamSchedule;
import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.assessments.api.ResultPolicy;
import com.saasresearcher.learning.assessments.dto.ExamDraftRequest;
import com.saasresearcher.learning.assessments.dto.ExamViews.ExamContent;
import com.saasresearcher.learning.assessments.dto.ExamViews.ExamView;
import com.saasresearcher.learning.assessments.dto.ExamViews.QuestionContent;
import com.saasresearcher.learning.assessments.dto.ExamViews.ScheduleCommand;
import com.saasresearcher.learning.assessments.entity.Exam;
import com.saasresearcher.learning.assessments.repository.ExamRepository;
import com.saasresearcher.learning.assessments.service.ExamContentStore.Summary;
import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.courses.api.CourseCatalogService;
import com.saasresearcher.learning.courses.api.CourseOwnershipService;
import com.saasresearcher.learning.courses.api.CourseVersionInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExamAuthoringService {

    static final String EXAM_FROZEN = "EXAM_FROZEN";
    static final BigDecimal DEFAULT_PASS = BigDecimal.valueOf(50);

    private final ExamRepository examRepository;
    private final ExamContentStore examContentStore;
    private final CourseOwnershipService courseOwnershipService;
    private final CourseCatalogService courseCatalogService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ExamView> list(UUID courseId, UUID versionId, Actor actor) {
        courseOwnershipService.requireAuthor(courseId, actor);
        CourseVersionInfo version = versionId == null
                ? courseCatalogService.draftOf(courseId).or(() -> courseCatalogService.latestPublished(courseId)
                        .map(course -> new CourseVersionInfo(course.versionId(), courseId, course.edition(), false)))
                        .orElseThrow(ApiException::notFound)
                : courseCatalogService.requireVersion(versionId);
        if (!version.courseId().equals(courseId)) {
            throw ApiException.notFound();
        }
        return examRepository.findByCourseVersionIdOrderByCreatedAtAscIdAsc(version.versionId()).stream().map(exam -> view(exam, version.draft())).toList();
    }

    @Transactional
    public ExamView createExam(UUID courseId, String title, boolean required, ScheduleCommand command, Actor actor) {
        courseOwnershipService.requireAuthor(courseId, actor);
        CourseVersionInfo draft = courseCatalogService.draftOf(courseId)
                .orElseThrow(() -> ApiException.conflict("NO_DRAFT", "Start a course draft before adding exams"));
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty() || trimmed.length() > 200) {
            throw ApiException.invalid("title", "Title must be 1-200 characters");
        }
        ExamSchedule schedule = ScheduleRules.validated(command, false, required);
        Exam exam = examRepository.saveAndFlush(new Exam(courseId, draft.versionId(), trimmed, "", DEFAULT_PASS,
                required, schedule, clock.instant()));
        auditService.record(AuditEntry.success(actor.userId(), "exam.created", "exam", exam.getId(), Map.of("required", required)));
        return view(exam, true);
    }

    @Transactional(readOnly = true)
    public ExamContent getExam(UUID examId, Actor actor) {
        Exam exam = examRepository.findById(examId).orElseThrow(ApiException::notFound);
        courseOwnershipService.requireAuthor(exam.getCourseId(), actor);
        return contentOf(exam, courseCatalogService.requireVersion(exam.getCourseVersionId()).draft());
    }

    @Transactional
    public ExamContent saveExam(UUID examId, ExamDraftRequest input, Actor actor) {
        Exam exam = lockDraftExam(examId, actor, "Published exams cannot change; edit them in a new course draft");
        Revisions.require(input.expectedRevision(), exam.getVersion());
        List<QuestionContent> questions = ExamRules.normalize(input, examContentStore.questionIds(examId), examContentStore.optionIds(examId));
        Instant now = clock.instant();
        exam.edit(input.title().trim(), input.instructions() == null ? "" : input.instructions().trim(),
                input.passPercent() == null ? DEFAULT_PASS : input.passPercent(), now);
        if (exam.getResultPolicy() == ResultPolicy.AUTO_PUBLISH && questions.stream().anyMatch(question -> question.type() == QuestionType.WRITTEN)) {
            ExamSchedule schedule = exam.schedule();
            exam.reschedule(new ExamSchedule(schedule.mode(), schedule.opensAt(), schedule.closesAt(), schedule.durationMinutes(),
                    schedule.maxAttempts(), ResultPolicy.TEACHER_PUBLISH, schedule.feedbackPolicy()), now);
        }
        examRepository.flush();
        examContentStore.replace(examId, questions);
        return contentOf(exam, true);
    }

    @Transactional
    public ExamView updateSchedule(UUID examId, long expectedRevision, Boolean required, ScheduleCommand command, Actor actor) {
        Exam exam = lockExam(examId, actor);
        Revisions.require(expectedRevision, exam.getVersion());
        boolean draft = courseCatalogService.requireVersion(exam.getCourseVersionId()).draft();
        boolean requiredNow = required == null ? exam.isRequired() : required;
        if (!draft && requiredNow != exam.isRequired()) {
            throw ApiException.conflict(EXAM_FROZEN, "Published editions keep their required exams; change them in a new edition");
        }
        ExamSchedule schedule = ScheduleRules.validated(command, examContentStore.summary(examId).mixed(), requiredNow);
        Instant now = clock.instant();
        exam.setRequired(requiredNow, now);
        exam.reschedule(schedule, now);
        examRepository.flush();
        auditService.record(AuditEntry.success(actor.userId(), "exam.rescheduled", "exam", examId,
                Map.of("mode", schedule.mode(), "opensAt", String.valueOf(schedule.opensAt()), "closesAt", String.valueOf(schedule.closesAt()))));
        return view(exam, draft);
    }

    @Transactional
    public void deleteExam(UUID examId, Actor actor) {
        Exam exam = lockDraftExam(examId, actor, "Exams of a published edition cannot be removed");
        examContentStore.replace(examId, List.of());
        examRepository.delete(exam);
        auditService.record(AuditEntry.success(actor.userId(), "exam.deleted", "exam", examId));
    }

    private Exam lockExam(UUID examId, Actor actor) {
        Exam unlocked = examRepository.findById(examId).orElseThrow(ApiException::notFound);
        courseOwnershipService.requireAuthor(unlocked.getCourseId(), actor);
        courseOwnershipService.lock(unlocked.getCourseId());
        return examRepository.findById(examId).orElseThrow();
    }

    private Exam lockDraftExam(UUID examId, Actor actor, String frozenMessage) {
        Exam exam = lockExam(examId, actor);
        if (!courseCatalogService.requireVersion(exam.getCourseVersionId()).draft()) {
            throw ApiException.conflict(EXAM_FROZEN, frozenMessage);
        }
        return exam;
    }

    private ExamView view(Exam exam, boolean draft) {
        Summary summary = examContentStore.summary(exam.getId());
        ExamSchedule schedule = exam.schedule();
        return new ExamView(exam.getId(), exam.getCourseId(), exam.getCourseVersionId(), draft, exam.getTitle(), summary.questionCount(), summary.maxPoints(),
                summary.mixed(), exam.isRequired(), schedule.mode(), schedule.opensAt(), schedule.closesAt(),
                schedule.durationMinutes(), schedule.maxAttempts(), schedule.resultPolicy(), schedule.feedbackPolicy(), exam.getVersion());
    }

    private ExamContent contentOf(Exam exam, boolean draft) {
        return new ExamContent(exam.getId(), exam.getCourseId(), exam.getCourseVersionId(), draft, exam.getVersion(), exam.getTitle(), exam.getInstructions(),
                exam.getPassPercent(), examContentStore.getQuestionContents(exam.getId()));
    }
}
