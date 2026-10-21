package com.saasresearcher.learning.learning.repository;

import com.saasresearcher.learning.learning.dto.ProgressView;
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
public class ProgressRepository {

    private static final String COLUMNS = "lesson_id, completed_at, resume_seconds, revision, last_activity_at";

    private final JdbcClient jdbc;

    ProgressRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProgressView> forEnrollment(UUID enrollmentId) {
        return jdbc.sql("select " + COLUMNS + " from lesson_progress where enrollment_id = :enrollment")
                .param("enrollment", enrollmentId).query(ProgressRepository::row).list();
    }

    public Optional<ProgressView> find(UUID enrollmentId, UUID lessonId) {
        return jdbc.sql("select " + COLUMNS + " from lesson_progress where enrollment_id = :enrollment and lesson_id = :lesson")
                .param("enrollment", enrollmentId).param("lesson", lessonId).query(ProgressRepository::row).optional();
    }

    public void upsert(UUID enrollmentId, ProgressView progress) {
        jdbc.sql("""
                insert into lesson_progress (id, enrollment_id, lesson_id, completed_at, resume_seconds, last_activity_at, revision)
                values (:id, :enrollment, :lesson, :completedAt, :resume, :activity, :revision)
                on conflict (enrollment_id, lesson_id) do update set completed_at = excluded.completed_at,
                    resume_seconds = excluded.resume_seconds, last_activity_at = excluded.last_activity_at, revision = excluded.revision
                """)
                .param("id", UUID.randomUUID()).param("enrollment", enrollmentId).param("lesson", progress.lessonId())
                .param("completedAt", progress.completedAt() == null ? null : Timestamp.from(progress.completedAt()))
                .param("resume", progress.resumeSeconds()).param("activity", Timestamp.from(progress.lastActivityAt()))
                .param("revision", progress.revision())
                .update();
    }

    private static ProgressView row(ResultSet rs, int row) throws SQLException {
        Timestamp completedAt = rs.getTimestamp("completed_at");
        Instant completed = completedAt == null ? null : completedAt.toInstant();
        return new ProgressView(rs.getObject("lesson_id", UUID.class), completed != null, completed, rs.getLong("resume_seconds"),
                rs.getLong("revision"), rs.getTimestamp("last_activity_at").toInstant());
    }
}
