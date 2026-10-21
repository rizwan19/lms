package com.saasresearcher.learning.assessments.service;

import com.saasresearcher.learning.assessments.dto.ExamDraftInput;
import com.saasresearcher.learning.assessments.dto.ExamViews.DraftHeader;
import com.saasresearcher.learning.assessments.dto.ExamViews.EditionView;
import com.saasresearcher.learning.assessments.dto.ExamViews.ExamContent;
import com.saasresearcher.learning.assessments.dto.ExamViews.ExamDetail;
import com.saasresearcher.learning.assessments.entity.ExamQuestion.Option;
import com.saasresearcher.learning.assessments.entity.ExamQuestion;
import com.saasresearcher.learning.assessments.entity.ExamRows.ExamRow;
import com.saasresearcher.learning.assessments.entity.ExamRows.ExamSummary;
import com.saasresearcher.learning.assessments.entity.ExamRows.VersionRow;
import com.saasresearcher.learning.assessments.repository.ExamContentRepository;
import com.saasresearcher.learning.assessments.repository.ExamRepository;
import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.ErrorCodes;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.common.Role;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exam drafts and immutable editions; mirrors course publication (one draft, numbered sealed editions). */
@Service
public class ExamAuthoringService {

    private final ExamRepository exams;
    private final ExamContentRepository content;
    private final ExamAccess access;
    private final AuditLog audit;
    private final Clock clock;

    ExamAuthoringService(ExamRepository exams, ExamContentRepository content, ExamAccess access, AuditLog audit, Clock clock) {
        this.exams = exams;
        this.content = content;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public UUID create(UUID courseId, String title, Actor actor) {
        if (!actor.isAdministrator() && !actor.has(Role.TEACHER)) {
            throw ApiException.accessDenied("Teacher or administrator role required");
        }
        access.requireCourseAuthor(courseId, actor);
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty() || trimmed.length() > 200) {
            throw ApiException.invalid("title", "Title must be 1-200 characters");
        }
        UUID examId = UUID.randomUUID();
        exams.insertExam(examId, courseId, trimmed, actor.userId(), clock.instant());
        exams.insertDraft(UUID.randomUUID(), examId, trimmed, "", BigDecimal.valueOf(50), null, clock.instant());
        audit.record(AuditEntry.success(actor.userId(), "exam.created", "exam", examId));
        return examId;
    }

    @Transactional(readOnly = true)
    public List<ExamSummary> list(UUID courseId, Actor actor) {
        access.requireCourseAuthor(courseId, actor);
        return exams.examsOfCourse(courseId);
    }

    @Transactional(readOnly = true)
    public ExamDetail detail(UUID examId, Actor actor) {
        ExamRow exam = access.requireExam(examId, actor);
        List<EditionView> editions = exams.editions(examId).stream()
                .map(row -> new EditionView(row.id(), row.edition(), row.title(), row.publishedAt())).toList();
        DraftHeader draft = exams.findDraft(examId).map(row -> new DraftHeader(row.id(), row.version())).orElse(null);
        return new ExamDetail(exam.id(), exam.courseId(), exam.title(), editions, draft);
    }

    @Transactional(readOnly = true)
    public ExamContent draft(UUID examId, Actor actor) {
        access.requireExam(examId, actor);
        return contentOf(requireDraft(examId));
    }

    @Transactional
    public ExamContent saveDraft(UUID examId, ExamDraftInput input, Actor actor) {
        access.requireExam(examId, actor);
        VersionRow draft = requireDraft(examId);
        List<ExamQuestion> questions = ExamRules.normalize(input, content.questionIds(draft.id()), content.optionIds(draft.id()));
        BigDecimal pass = input.passPercent() == null ? BigDecimal.valueOf(50) : input.passPercent();
        if (!exams.updateDraft(draft.id(), input.expectedRevision(), input.title().trim(),
                input.instructions() == null ? "" : input.instructions().trim(), pass, clock.instant())) {
            throw ApiException.conflict(ErrorCodes.REVISION_CONFLICT, "The draft was changed by another request; reload and retry");
        }
        content.replace(draft.id(), questions);
        return contentOf(requireDraft(examId));
    }

    @Transactional
    public ExamContent startDraft(UUID examId, UUID fromVersionId, Actor actor) {
        access.requireExam(examId, actor);
        exams.lockExam(examId);
        if (exams.findDraft(examId).isPresent()) {
            throw ApiException.conflict("DRAFT_EXISTS", "This exam already has a draft");
        }
        VersionRow source = exams.findVersion(fromVersionId)
                .filter(row -> !row.isDraft() && row.examId().equals(examId))
                .orElseThrow(ApiException::notFound);
        UUID draftId = UUID.randomUUID();
        exams.insertDraft(draftId, examId, source.title(), source.instructions(), source.passPercent(), source.id(), clock.instant());
        content.replace(draftId, content.questions(source.id()).stream()
                .map(question -> new ExamQuestion(UUID.randomUUID(), question.type(), question.prompt(), question.points(), question.explanation(),
                        question.gradingNotes(), question.options().stream().map(option -> new Option(UUID.randomUUID(), option.label(), option.correct())).toList()))
                .toList());
        return contentOf(requireDraft(examId));
    }

    /** Seals the draft under the exam lock so two concurrent publishes cannot both succeed. */
    @Transactional
    public ExamContent publish(UUID examId, long expectedRevision, Actor actor) {
        access.requireExam(examId, actor);
        exams.lockExam(examId);
        VersionRow draft = exams.lockDraft(examId).orElseThrow(() -> ApiException.conflict("NO_DRAFT", "There is no draft to publish"));
        Revisions.require(expectedRevision, draft.version());
        ExamRules.requirePublishable(draft.title(), content.questions(draft.id()));
        int edition = exams.nextEdition(examId);
        exams.publish(draft.id(), edition, actor.userId(), clock.instant());
        exams.renameExam(examId, draft.title(), clock.instant());
        audit.record(AuditEntry.success(actor.userId(), "exam.published", "exam", examId, Map.of("edition", edition)));
        return contentOf(exams.findVersion(draft.id()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public ExamContent preview(UUID examId, UUID versionId, Actor actor) {
        access.requireExam(examId, actor);
        return contentOf(exams.findVersion(versionId).filter(row -> row.examId().equals(examId)).orElseThrow(ApiException::notFound));
    }

    private VersionRow requireDraft(UUID examId) {
        return exams.findDraft(examId).orElseThrow(() -> ApiException.conflict("NO_DRAFT", "This exam has no draft; start one from an edition"));
    }

    private ExamContent contentOf(VersionRow version) {
        return new ExamContent(version.id(), version.examId(), version.edition(), version.version(), version.title(), version.instructions(),
                version.passPercent(), content.questions(version.id()));
    }
}
