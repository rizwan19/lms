package com.saasresearcher.learning.grading.internal;

import com.saasresearcher.learning.classrooms.api.TeachingAccess;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read model of a class's finalized attempts and their grade state; oldest unpublished work first. */
@Component
class GradingQueue {

    enum Filter { PENDING, PUBLISHED, ALL }

    private static final String FROM = """
            from exam_attempts a
            join exam_assignments s on s.id = a.assignment_id
            join exam_versions v on v.id = s.exam_version_id
            join users u on u.id = a.student_id
            join grade_revisions r on r.attempt_id = a.id and r.revision_number = (select max(revision_number) from grade_revisions x where x.attempt_id = a.id)
            where s.class_id = :class and a.status <> 'IN_PROGRESS'
              and (:filter = 'ALL' or (:filter = 'PENDING' and r.status in ('DRAFT', 'FINALIZED')) or (:filter = 'PUBLISHED' and r.status = 'PUBLISHED'))
            """;

    private final JdbcClient jdbc;
    private final TeachingAccess teaching;

    GradingQueue(JdbcClient jdbc, TeachingAccess teaching) {
        this.jdbc = jdbc;
        this.teaching = teaching;
    }

    record QueueRow(UUID attemptId, String studentName, String username, String examTitle, int attemptNumber, Instant finalizedAt,
            String gradeStatus, int revisionNumber, BigDecimal publishedEarned, BigDecimal maximum) {
    }

    @Transactional(readOnly = true)
    PageResponse<QueueRow> list(UUID classId, Filter filter, PageQuery page, Actor actor) {
        teaching.requireClassTeacher(classId, actor);
        List<QueueRow> rows = jdbc.sql("""
                select a.id, u.display_name, u.username, v.title, a.attempt_number, a.finalized_at, r.status, r.revision_number,
                    a.published_earned, a.max_score
                """ + FROM + " order by case when r.status in ('DRAFT', 'FINALIZED') then 0 else 1 end, a.finalized_at, a.id limit :limit offset :offset")
                .param("class", classId).param("filter", filter.name()).param("limit", page.size()).param("offset", page.offset())
                .query((rs, row) -> new QueueRow(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("username"),
                        rs.getString("title"), rs.getInt("attempt_number"), rs.getTimestamp("finalized_at").toInstant(), rs.getString("status"),
                        rs.getInt("revision_number"), rs.getBigDecimal("published_earned"), rs.getBigDecimal("max_score")))
                .list();
        long total = jdbc.sql("select count(*) " + FROM).param("class", classId).param("filter", filter.name()).query(Long.class).single();
        return PageResponse.of(rows, page, total);
    }
}
