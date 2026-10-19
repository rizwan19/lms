package com.saasresearcher.learning.reporting.internal;

import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.reporting.api.StudentProgressRow;
import com.saasresearcher.learning.reporting.api.StudentScoreRow.Score;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read-only projections over the transactional tables (no separate analytics store). Only published results are used
 * for scores; pending grading is reported as a count, never as zero.
 */
@Repository
class ClassReportQueries {

    private final JdbcClient jdbc;

    ClassReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Student(UUID enrollmentId, String displayName, String username, String status, Instant enrolledAt) {
    }

    long countStudents(UUID classId) {
        return jdbc.sql("select count(*) from enrollments where class_id = :class").param("class", classId).query(Long.class).single();
    }

    List<Student> students(UUID classId, PageQuery page) {
        return jdbc.sql("""
                select e.id, u.display_name, u.username, e.status, e.enrolled_at from enrollments e join users u on u.id = e.student_id
                where e.class_id = :class order by lower(u.display_name), e.id limit :limit offset :offset
                """)
                .param("class", classId).param("limit", page.size()).param("offset", page.offset())
                .query((rs, row) -> new Student(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("username"),
                        rs.getString("status"), rs.getTimestamp("enrolled_at").toInstant()))
                .list();
    }

    StudentProgressRow progress(Student student, Set<UUID> requiredLessonIds, int inactivityDays, Instant now) {
        int completed = requiredLessonIds.isEmpty() ? 0 : jdbc.sql("""
                select count(*) from lesson_progress where enrollment_id = :enrollment and completed_at is not null and lesson_id in (:lessons)
                """).param("enrollment", student.enrollmentId()).param("lessons", requiredLessonIds).query(Integer.class).single();
        Timestamp activity = jdbc.sql("""
                select greatest(
                    (select max(last_activity_at) from lesson_progress where enrollment_id = :enrollment),
                    (select max(coalesce(finalized_at, started_at)) from exam_attempts where enrollment_id = :enrollment),
                    (select max(x.saved_at) from attempt_answers x join exam_attempts a on a.id = x.attempt_id where a.enrollment_id = :enrollment))
                """).param("enrollment", student.enrollmentId()).query(Timestamp.class).optional().orElse(null);
        Map<String, Object> exams = jdbc.sql("""
                select count(*) as required,
                    count(*) filter (where exists (select 1 from exam_attempts a where a.assignment_id = s.id and a.enrollment_id = :enrollment
                        and a.published_passed)) as passed
                from exam_assignments s join enrollments e on e.class_id = s.class_id where e.id = :enrollment and s.required
                """).param("enrollment", student.enrollmentId()).query().singleRow();
        int pending = jdbc.sql("""
                select count(*) from exam_attempts where enrollment_id = :enrollment and status <> 'IN_PROGRESS' and published_at is null
                """).param("enrollment", student.enrollmentId()).query(Integer.class).single();
        String completion = jdbc.sql("select status from course_completions where enrollment_id = :enrollment")
                .param("enrollment", student.enrollmentId()).query(String.class).optional().orElse("NOT_COMPLETED");
        Instant lastActivity = activity == null ? null : activity.toInstant();
        Instant reference = lastActivity == null ? student.enrolledAt() : lastActivity;
        boolean inactive = reference.isBefore(now.minus(Duration.ofDays(inactivityDays)));
        Integer percent = requiredLessonIds.isEmpty() ? null : (int) Math.floor(completed * 100.0 / requiredLessonIds.size());
        return new StudentProgressRow(student.enrollmentId(), student.displayName(), student.username(), student.status(), requiredLessonIds.size(),
                completed, percent, ((Number) exams.get("required")).intValue(), ((Number) exams.get("passed")).intValue(), pending, lastActivity, inactive,
                completion);
    }

    record Assignment(UUID id, String title, boolean required) {
    }

    List<Assignment> assignments(UUID classId) {
        return jdbc.sql("""
                select s.id, v.title, s.required from exam_assignments s join exam_versions v on v.id = s.exam_version_id
                where s.class_id = :class order by s.required desc, s.created_at, s.id
                """).param("class", classId)
                .query((rs, row) -> new Assignment(rs.getObject("id", UUID.class), rs.getString("title"), rs.getBoolean("required"))).list();
    }

    /** Best published attempt per assignment for one enrollment (highest score, then latest publication). */
    List<Score> scores(UUID enrollmentId, List<Assignment> assignments) {
        Map<UUID, Score> best = new LinkedHashMap<>();
        jdbc.sql("""
                select distinct on (a.assignment_id) a.assignment_id, a.published_earned, a.published_maximum, a.published_passed, a.published_revision_id
                from exam_attempts a where a.enrollment_id = :enrollment and a.published_at is not null
                order by a.assignment_id, a.published_earned desc, a.published_at desc
                """).param("enrollment", enrollmentId).query(rs -> {
                    UUID assignment = rs.getObject("assignment_id", UUID.class);
                    best.put(assignment, new Score(assignment, null, false, rs.getBigDecimal("published_earned"), rs.getBigDecimal("published_maximum"),
                            rs.getBoolean("published_passed"), rs.getObject("published_revision_id", UUID.class), false));
                });
        List<UUID> pending = jdbc.sql("""
                select distinct assignment_id from exam_attempts where enrollment_id = :enrollment and status <> 'IN_PROGRESS' and published_at is null
                """).param("enrollment", enrollmentId).query(UUID.class).list();
        List<Score> scores = new ArrayList<>();
        for (Assignment assignment : assignments) {
            Score found = best.get(assignment.id());
            BigDecimal earned = found == null ? null : found.bestEarned();
            scores.add(new Score(assignment.id(), assignment.title(), assignment.required(), earned, found == null ? null : found.maximum(),
                    found == null ? null : found.passed(), found == null ? null : found.publishedRevisionId(), pending.contains(assignment.id())));
        }
        return scores;
    }
}
