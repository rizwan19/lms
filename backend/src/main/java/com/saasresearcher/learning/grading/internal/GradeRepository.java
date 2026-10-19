package com.saasresearcher.learning.grading.internal;

import com.saasresearcher.learning.grading.internal.GradeRows.GradeRow;
import com.saasresearcher.learning.grading.internal.GradeRows.RevisionRow;
import com.saasresearcher.learning.grading.internal.GradeRows.RevisionStatus;
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

@Repository
class GradeRepository {

    private static final String COLUMNS = "id, attempt_id, revision_number, status, earned, maximum, reason, published_at, version";

    private final JdbcClient jdbc;

    GradeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insertRevision(UUID id, UUID attemptId, int number, BigDecimal maximum, String reason, UUID createdBy, Instant now) {
        jdbc.sql("""
                insert into grade_revisions (id, attempt_id, revision_number, status, maximum, reason, created_by, created_at)
                values (:id, :attempt, :number, 'DRAFT', :maximum, :reason, :by, :now)
                """)
                .param("id", id).param("attempt", attemptId).param("number", number).param("maximum", maximum)
                .param("reason", reason).param("by", createdBy).param("now", Timestamp.from(now))
                .update();
    }

    void insertGrade(UUID revisionId, GradeRow grade, UUID gradedBy, Instant now) {
        jdbc.sql("""
                insert into answer_grades (grade_revision_id, question_id, points, feedback, graded_by, graded_at)
                values (:revision, :question, :points, :feedback, :by, :at)
                """)
                .param("revision", revisionId).param("question", grade.questionId()).param("points", grade.points())
                .param("feedback", grade.feedback()).param("by", gradedBy).param("at", grade.points() == null ? null : Timestamp.from(now))
                .update();
    }

    void updateGrade(UUID revisionId, GradeRow grade, UUID gradedBy, Instant now) {
        jdbc.sql("""
                update answer_grades set points = :points, feedback = :feedback, graded_by = :by, graded_at = :at
                where grade_revision_id = :revision and question_id = :question
                """)
                .param("revision", revisionId).param("question", grade.questionId()).param("points", grade.points())
                .param("feedback", grade.feedback()).param("by", gradedBy).param("at", Timestamp.from(now))
                .update();
    }

    List<GradeRow> grades(UUID revisionId) {
        return jdbc.sql("select question_id, points, feedback from answer_grades where grade_revision_id = :revision")
                .param("revision", revisionId)
                .query((rs, row) -> new GradeRow(rs.getObject("question_id", UUID.class), rs.getBigDecimal("points"), rs.getString("feedback")))
                .list();
    }

    List<GradeRow> publishedGrades(UUID revisionId) {
        return jdbc.sql("select question_id, points, feedback from published_question_grades where grade_revision_id = :revision")
                .param("revision", revisionId)
                .query((rs, row) -> new GradeRow(rs.getObject("question_id", UUID.class), rs.getBigDecimal("points"), rs.getString("feedback")))
                .list();
    }

    List<RevisionRow> revisions(UUID attemptId) {
        return jdbc.sql("select " + COLUMNS + " from grade_revisions where attempt_id = :attempt order by revision_number")
                .param("attempt", attemptId).query(GradeRepository::row).list();
    }

    /** Locks all revisions of the attempt (there are few) so lifecycle changes serialize. */
    List<RevisionRow> lockRevisions(UUID attemptId) {
        return jdbc.sql("select " + COLUMNS + " from grade_revisions where attempt_id = :attempt order by revision_number for update")
                .param("attempt", attemptId).query(GradeRepository::row).list();
    }

    Optional<RevisionRow> published(UUID attemptId) {
        return jdbc.sql("select " + COLUMNS + " from grade_revisions where attempt_id = :attempt and status = 'PUBLISHED'")
                .param("attempt", attemptId).query(GradeRepository::row).optional();
    }

    void setStatus(UUID revisionId, RevisionStatus status, BigDecimal earned, Instant now) {
        jdbc.sql("""
                update grade_revisions set status = :status, earned = coalesce(:earned, earned),
                    finalized_at = case when :status = 'FINALIZED' then :now else finalized_at end, version = version + 1
                where id = :id
                """)
                .param("status", status.name()).param("earned", earned).param("now", Timestamp.from(now)).param("id", revisionId)
                .update();
    }

    void touch(UUID revisionId) {
        jdbc.sql("update grade_revisions set version = version + 1 where id = :id").param("id", revisionId).update();
    }

    void markPublished(UUID revisionId, UUID publishedBy, Instant now) {
        jdbc.sql("update grade_revisions set status = 'PUBLISHED', published_at = :now, published_by = :by, version = version + 1 where id = :id")
                .param("now", Timestamp.from(now)).param("by", publishedBy).param("id", revisionId).update();
        jdbc.sql("""
                insert into published_question_grades (grade_revision_id, question_id, points, feedback)
                select grade_revision_id, question_id, points, feedback from answer_grades where grade_revision_id = :id
                """).param("id", revisionId).update();
    }

    private static RevisionRow row(ResultSet rs, int row) throws SQLException {
        Timestamp publishedAt = rs.getTimestamp("published_at");
        return new RevisionRow(rs.getObject("id", UUID.class), rs.getObject("attempt_id", UUID.class), rs.getInt("revision_number"),
                RevisionStatus.valueOf(rs.getString("status")), rs.getBigDecimal("earned"), rs.getBigDecimal("maximum"), rs.getString("reason"),
                publishedAt == null ? null : publishedAt.toInstant(), rs.getLong("version"));
    }
}
