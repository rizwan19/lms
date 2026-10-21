package com.saasresearcher.learning.assessments.repository;

import com.saasresearcher.learning.assessments.entity.AssignmentRow;
import com.saasresearcher.learning.assessments.entity.Schedule.FeedbackPolicy;
import com.saasresearcher.learning.assessments.entity.Schedule.ResultPolicy;
import com.saasresearcher.learning.assessments.entity.Schedule;
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
public class AssignmentRepository {

    private static final String COLUMNS =
            "id, class_id, exam_version_id, required, opens_at, closes_at, duration_minutes, max_attempts, result_policy, feedback_policy, version";

    private final JdbcClient jdbc;

    AssignmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts unless the class already has this exam edition; returns whether a row was added. */
    public boolean insert(UUID id, UUID classId, UUID examVersionId, boolean required, Schedule schedule, Instant now) {
        return jdbc.sql("""
                insert into exam_assignments (id, class_id, exam_version_id, required, opens_at, closes_at, duration_minutes, max_attempts,
                    result_policy, feedback_policy, created_at, updated_at)
                values (:id, :class, :exam, :required, :opens, :closes, :duration, :attempts, :result, :feedback, :now, :now)
                on conflict (class_id, exam_version_id) do nothing
                """)
                .param("id", id).param("class", classId).param("exam", examVersionId).param("required", required)
                .param("opens", timestamp(schedule.opensAt())).param("closes", timestamp(schedule.closesAt()))
                .param("duration", schedule.durationMinutes()).param("attempts", schedule.maxAttempts())
                .param("result", schedule.resultPolicy().name()).param("feedback", schedule.feedbackPolicy().name())
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    public boolean update(UUID id, long expectedRevision, Schedule schedule, Instant now) {
        return jdbc.sql("""
                update exam_assignments set opens_at = :opens, closes_at = :closes, duration_minutes = :duration, max_attempts = :attempts,
                    result_policy = :result, feedback_policy = :feedback, updated_at = :now, version = version + 1
                where id = :id and version = :expected
                """)
                .param("id", id).param("expected", expectedRevision)
                .param("opens", timestamp(schedule.opensAt())).param("closes", timestamp(schedule.closesAt()))
                .param("duration", schedule.durationMinutes()).param("attempts", schedule.maxAttempts())
                .param("result", schedule.resultPolicy().name()).param("feedback", schedule.feedbackPolicy().name())
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    public Optional<AssignmentRow> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from exam_assignments where id = :id").param("id", id).query(AssignmentRepository::row).optional();
    }

    public List<AssignmentRow> forClass(UUID classId) {
        return jdbc.sql("select " + COLUMNS + " from exam_assignments where class_id = :class order by required desc, created_at, id")
                .param("class", classId).query(AssignmentRepository::row).list();
    }

    private static AssignmentRow row(ResultSet rs, int row) throws SQLException {
        Schedule schedule = new Schedule(instant(rs.getTimestamp("opens_at")), instant(rs.getTimestamp("closes_at")),
                (Integer) rs.getObject("duration_minutes"), rs.getInt("max_attempts"), ResultPolicy.valueOf(rs.getString("result_policy")),
                FeedbackPolicy.valueOf(rs.getString("feedback_policy")));
        return new AssignmentRow(rs.getObject("id", UUID.class), rs.getObject("class_id", UUID.class), rs.getObject("exam_version_id", UUID.class),
                rs.getBoolean("required"), schedule, rs.getLong("version"));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
