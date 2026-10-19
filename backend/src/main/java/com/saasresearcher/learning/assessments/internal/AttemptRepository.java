package com.saasresearcher.learning.assessments.internal;

import com.saasresearcher.learning.assessments.api.AttemptStatus;
import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.assessments.internal.AttemptRows.Answer;
import com.saasresearcher.learning.assessments.internal.AttemptRows.AttemptRow;
import com.saasresearcher.learning.assessments.internal.AttemptRows.FinalizedReason;
import com.saasresearcher.learning.assessments.internal.AttemptRows.SnapshotOption;
import com.saasresearcher.learning.assessments.internal.AttemptRows.SnapshotQuestion;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class AttemptRepository {

    private static final String COLUMNS = """
            id, assignment_id, enrollment_id, student_id, attempt_number, status, started_at, expires_at, finalized_at,
            finalized_reason, objective_score, max_score""";
    private static final TypeReference<List<SnapshotOption>> OPTIONS = new TypeReference<>() {
    };
    private static final TypeReference<List<UUID>> IDS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AttemptRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void insert(AttemptRow attempt, List<SnapshotQuestion> questions) {
        jdbc.sql("""
                insert into exam_attempts (id, assignment_id, enrollment_id, student_id, attempt_number, status, started_at, expires_at, max_score)
                values (:id, :assignment, :enrollment, :student, :number, 'IN_PROGRESS', :started, :expires, :max)
                """)
                .param("id", attempt.id()).param("assignment", attempt.assignmentId()).param("enrollment", attempt.enrollmentId())
                .param("student", attempt.studentId()).param("number", attempt.attemptNumber())
                .param("started", Timestamp.from(attempt.startedAt())).param("expires", Timestamp.from(attempt.expiresAt()))
                .param("max", attempt.maxScore())
                .update();
        for (SnapshotQuestion question : questions) {
            jdbc.sql("""
                    insert into attempt_questions (attempt_id, question_id, position, type, prompt, points, explanation, grading_notes, options)
                    values (:attempt, :question, :position, :type, :prompt, :points, :explanation, :notes, cast(:options as jsonb))
                    """)
                    .param("attempt", attempt.id()).param("question", question.questionId()).param("position", question.position())
                    .param("type", question.type().name()).param("prompt", question.prompt()).param("points", question.points())
                    .param("explanation", question.explanation()).param("notes", question.gradingNotes())
                    .param("options", json.writeValueAsString(question.options()))
                    .update();
        }
    }

    Optional<AttemptRow> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from exam_attempts where id = :id").param("id", id).query(AttemptRepository::row).optional();
    }

    Optional<AttemptRow> lock(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from exam_attempts where id = :id for update").param("id", id).query(AttemptRepository::row).optional();
    }

    Optional<AttemptRow> findActive(UUID enrollmentId, UUID assignmentId) {
        return jdbc.sql("select " + COLUMNS + " from exam_attempts where enrollment_id = :enrollment and assignment_id = :assignment and status = 'IN_PROGRESS'")
                .param("enrollment", enrollmentId).param("assignment", assignmentId).query(AttemptRepository::row).optional();
    }

    List<AttemptRow> forEnrollment(UUID enrollmentId, UUID assignmentId) {
        return jdbc.sql("select " + COLUMNS + " from exam_attempts where enrollment_id = :enrollment and assignment_id = :assignment order by attempt_number")
                .param("enrollment", enrollmentId).param("assignment", assignmentId).query(AttemptRepository::row).list();
    }

    /** Attempts past their deadline that are still open; skips rows another worker is finalizing. */
    List<UUID> dueExpired(Instant now, int limit) {
        return jdbc.sql("select id from exam_attempts where status = 'IN_PROGRESS' and expires_at <= :now order by expires_at limit :limit")
                .param("now", Timestamp.from(now)).param("limit", limit).query(UUID.class).list();
    }

    List<SnapshotQuestion> questions(UUID attemptId) {
        return jdbc.sql("""
                select question_id, position, type, prompt, points, explanation, grading_notes, options::text as options, auto_points
                from attempt_questions where attempt_id = :attempt order by position
                """)
                .param("attempt", attemptId)
                .query((rs, row) -> new SnapshotQuestion(rs.getObject("question_id", UUID.class), rs.getInt("position"),
                        QuestionType.valueOf(rs.getString("type")), rs.getString("prompt"), rs.getBigDecimal("points"), rs.getString("explanation"),
                        rs.getString("grading_notes"), json.readValue(rs.getString("options"), OPTIONS), rs.getBigDecimal("auto_points")))
                .list();
    }

    List<Answer> answers(UUID attemptId) {
        return jdbc.sql("select question_id, selected_option_ids::text as selected, written_text, revision, saved_at from attempt_answers where attempt_id = :attempt")
                .param("attempt", attemptId).query(this::answer).list();
    }

    Optional<Answer> answer(UUID attemptId, UUID questionId) {
        return jdbc.sql("""
                select question_id, selected_option_ids::text as selected, written_text, revision, saved_at from attempt_answers
                where attempt_id = :attempt and question_id = :question
                """)
                .param("attempt", attemptId).param("question", questionId).query(this::answer).optional();
    }

    void saveAnswer(UUID attemptId, Answer answer) {
        jdbc.sql("""
                insert into attempt_answers (attempt_id, question_id, selected_option_ids, written_text, revision, saved_at)
                values (:attempt, :question, cast(:selected as jsonb), :text, :revision, :saved)
                on conflict (attempt_id, question_id) do update set selected_option_ids = excluded.selected_option_ids,
                    written_text = excluded.written_text, revision = excluded.revision, saved_at = excluded.saved_at
                """)
                .param("attempt", attemptId).param("question", answer.questionId())
                .param("selected", json.writeValueAsString(answer.selectedOptionIds())).param("text", answer.writtenText())
                .param("revision", answer.revision()).param("saved", Timestamp.from(answer.savedAt()))
                .update();
    }

    void setAutoPoints(UUID attemptId, UUID questionId, BigDecimal points) {
        jdbc.sql("update attempt_questions set auto_points = :points where attempt_id = :attempt and question_id = :question")
                .param("points", points).param("attempt", attemptId).param("question", questionId).update();
    }

    void finalize(UUID attemptId, AttemptStatus status, FinalizedReason reason, BigDecimal objectiveScore, Instant now) {
        jdbc.sql("""
                update exam_attempts set status = :status, finalized_reason = :reason, finalized_at = :now, objective_score = :score,
                    version = version + 1
                where id = :id and status = 'IN_PROGRESS'
                """)
                .param("status", status.name()).param("reason", reason.name()).param("now", Timestamp.from(now))
                .param("score", objectiveScore).param("id", attemptId)
                .update();
    }

    private Answer answer(ResultSet rs, int row) throws SQLException {
        return new Answer(rs.getObject("question_id", UUID.class), json.readValue(rs.getString("selected"), IDS), rs.getString("written_text"),
                rs.getLong("revision"), rs.getTimestamp("saved_at").toInstant());
    }

    private static AttemptRow row(ResultSet rs, int row) throws SQLException {
        Timestamp finalized = rs.getTimestamp("finalized_at");
        String reason = rs.getString("finalized_reason");
        return new AttemptRow(rs.getObject("id", UUID.class), rs.getObject("assignment_id", UUID.class), rs.getObject("enrollment_id", UUID.class),
                rs.getObject("student_id", UUID.class), rs.getInt("attempt_number"), AttemptStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("expires_at").toInstant(), finalized == null ? null : finalized.toInstant(),
                reason == null ? null : FinalizedReason.valueOf(reason), rs.getBigDecimal("objective_score"), rs.getBigDecimal("max_score"));
    }
}
