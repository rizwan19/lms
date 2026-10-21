package com.saasresearcher.learning.assessments.repository;

import com.saasresearcher.learning.assessments.entity.ExamRows.ExamRow;
import com.saasresearcher.learning.assessments.entity.ExamRows.ExamSummary;
import com.saasresearcher.learning.assessments.entity.ExamRows.VersionRow;
import com.saasresearcher.learning.assessments.entity.ExamRows.VersionStatus;
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

/** Exam identity and edition headers. */
@Repository
public class ExamRepository {

    private static final String VERSION_COLUMNS = "id, exam_id, edition, status, title, instructions, pass_percent, published_at, version";

    private final JdbcClient jdbc;

    ExamRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insertExam(UUID id, UUID courseId, String title, UUID createdBy, Instant now) {
        jdbc.sql("insert into exams (id, course_id, title, created_by, created_at, updated_at) values (:id, :course, :title, :by, :now, :now)")
                .param("id", id).param("course", courseId).param("title", title).param("by", createdBy).param("now", Timestamp.from(now))
                .update();
    }

    public Optional<ExamRow> findExam(UUID id) {
        return jdbc.sql("select id, course_id, title from exams where id = :id").param("id", id)
                .query((rs, row) -> new ExamRow(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class), rs.getString("title")))
                .optional();
    }

    public void lockExam(UUID id) {
        jdbc.sql("select id from exams where id = :id for update").param("id", id).query(UUID.class).optional();
    }

    public void renameExam(UUID id, String title, Instant now) {
        jdbc.sql("update exams set title = :title, updated_at = :now where id = :id")
                .param("id", id).param("title", title).param("now", Timestamp.from(now)).update();
    }

    public List<ExamSummary> examsOfCourse(UUID courseId) {
        return jdbc.sql("""
                select e.id, e.title,
                    (select max(edition) from exam_versions v where v.exam_id = e.id) as latest_edition,
                    (select id from exam_versions v where v.exam_id = e.id and v.status = 'PUBLISHED' order by edition desc limit 1) as latest_version,
                    (select version from exam_versions v where v.exam_id = e.id and v.status = 'DRAFT') as draft_revision
                from exams e where e.course_id = :course order by lower(e.title), e.id
                """)
                .param("course", courseId)
                .query((rs, row) -> new ExamSummary(rs.getObject("id", UUID.class), rs.getString("title"), (Integer) rs.getObject("latest_edition"),
                        rs.getObject("latest_version", UUID.class), (Long) rs.getObject("draft_revision")))
                .list();
    }

    public void insertDraft(UUID id, UUID examId, String title, String instructions, BigDecimal passPercent, UUID basedOn, Instant now) {
        jdbc.sql("""
                insert into exam_versions (id, exam_id, status, title, instructions, pass_percent, based_on_version_id, created_at, updated_at)
                values (:id, :exam, 'DRAFT', :title, :instructions, :pass, :basedOn, :now, :now)
                """)
                .param("id", id).param("exam", examId).param("title", title).param("instructions", instructions)
                .param("pass", passPercent).param("basedOn", basedOn).param("now", Timestamp.from(now))
                .update();
    }

    public Optional<VersionRow> findVersion(UUID id) {
        return jdbc.sql("select " + VERSION_COLUMNS + " from exam_versions where id = :id").param("id", id)
                .query(ExamRepository::versionRow).optional();
    }

    public Optional<VersionRow> findDraft(UUID examId) {
        return jdbc.sql("select " + VERSION_COLUMNS + " from exam_versions where exam_id = :exam and status = 'DRAFT'")
                .param("exam", examId).query(ExamRepository::versionRow).optional();
    }

    public Optional<VersionRow> lockDraft(UUID examId) {
        return jdbc.sql("select " + VERSION_COLUMNS + " from exam_versions where exam_id = :exam and status = 'DRAFT' for update")
                .param("exam", examId).query(ExamRepository::versionRow).optional();
    }

    public List<VersionRow> editions(UUID examId) {
        return jdbc.sql("select " + VERSION_COLUMNS + " from exam_versions where exam_id = :exam and status = 'PUBLISHED' order by edition desc")
                .param("exam", examId).query(ExamRepository::versionRow).list();
    }

    public boolean updateDraft(UUID id, long expectedRevision, String title, String instructions, BigDecimal passPercent, Instant now) {
        return jdbc.sql("""
                update exam_versions set title = :title, instructions = :instructions, pass_percent = :pass, updated_at = :now, version = version + 1
                where id = :id and status = 'DRAFT' and version = :expected
                """)
                .param("id", id).param("title", title).param("instructions", instructions).param("pass", passPercent)
                .param("now", Timestamp.from(now)).param("expected", expectedRevision)
                .update() == 1;
    }

    public void publish(UUID id, int edition, UUID publishedBy, Instant now) {
        jdbc.sql("""
                update exam_versions set status = 'PUBLISHED', edition = :edition, published_at = :now, published_by = :by,
                    updated_at = :now, version = version + 1
                where id = :id and status = 'DRAFT'
                """)
                .param("id", id).param("edition", edition).param("by", publishedBy).param("now", Timestamp.from(now))
                .update();
    }

    public int nextEdition(UUID examId) {
        return jdbc.sql("select coalesce(max(edition), 0) + 1 from exam_versions where exam_id = :exam").param("exam", examId)
                .query(Integer.class).single();
    }

    private static VersionRow versionRow(ResultSet rs, int row) throws SQLException {
        Timestamp publishedAt = rs.getTimestamp("published_at");
        return new VersionRow(rs.getObject("id", UUID.class), rs.getObject("exam_id", UUID.class), (Integer) rs.getObject("edition"),
                VersionStatus.valueOf(rs.getString("status")), rs.getString("title"), rs.getString("instructions"),
                rs.getBigDecimal("pass_percent"), publishedAt == null ? null : publishedAt.toInstant(), rs.getLong("version"));
    }
}
