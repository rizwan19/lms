package com.saasresearcher.learning.completion.repository;

import com.saasresearcher.learning.completion.api.CompletionSnapshot;
import com.saasresearcher.learning.completion.api.CompletionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CompletionRepository {

    private static final String COLUMNS = "id, enrollment_id, student_id, class_id, course_version_id, status, achieved_at";

    private final JdbcClient jdbc;

    CompletionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CompletionSnapshot> byEnrollment(UUID enrollmentId) {
        return jdbc.sql("select " + COLUMNS + " from course_completions where enrollment_id = :enrollment")
                .param("enrollment", enrollmentId).query(CompletionRepository::row).optional();
    }

    public Optional<CompletionSnapshot> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from course_completions where id = :id").param("id", id).query(CompletionRepository::row).optional();
    }

    public void insert(CompletionSnapshot completion) {
        jdbc.sql("""
                insert into course_completions (id, enrollment_id, student_id, class_id, course_version_id, status, achieved_at, status_changed_at)
                values (:id, :enrollment, :student, :class, :version, 'ACHIEVED', :at, :at)
                """)
                .param("id", completion.id()).param("enrollment", completion.enrollmentId()).param("student", completion.studentId())
                .param("class", completion.classId()).param("version", completion.courseVersionId())
                .param("at", Timestamp.from(completion.achievedAt()))
                .update();
    }

    public void setStatus(UUID id, CompletionStatus status, Instant now) {
        jdbc.sql("update course_completions set status = :status, status_changed_at = :now, version = version + 1 where id = :id")
                .param("status", status.name()).param("now", Timestamp.from(now)).param("id", id).update();
    }

    private static CompletionSnapshot row(ResultSet rs, int row) throws SQLException {
        return new CompletionSnapshot(rs.getObject("id", UUID.class), rs.getObject("enrollment_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getObject("class_id", UUID.class), rs.getObject("course_version_id", UUID.class), CompletionStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("achieved_at").toInstant());
    }
}
