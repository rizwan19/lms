package com.saasresearcher.learning.courses.internal;

import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.courses.internal.CourseRows.CourseRow;
import com.saasresearcher.learning.courses.internal.CourseRows.CourseStatus;
import com.saasresearcher.learning.courses.internal.CourseRows.CourseSummary;
import com.saasresearcher.learning.courses.internal.CourseRows.VersionRow;
import com.saasresearcher.learning.courses.internal.CourseRows.VersionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Course identity, teacher assignments and edition headers. */
@Repository
class CourseRepository {

    private static final String VERSION_COLUMNS =
            "id, course_id, edition, status, title, description, based_on_version_id, published_at, version";

    private final JdbcClient jdbc;

    CourseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insertCourse(UUID id, String title, UUID createdBy, Instant now) {
        jdbc.sql("insert into courses (id, title, status, created_by, created_at, updated_at) values (:id, :title, 'ACTIVE', :by, :now, :now)")
                .param("id", id).param("title", title).param("by", createdBy).param("now", Timestamp.from(now))
                .update();
    }

    Optional<CourseRow> findCourse(UUID id) {
        return jdbc.sql("select id, title, status, created_by, updated_at, version from courses where id = :id")
                .param("id", id).query(CourseRepository::courseRow).optional();
    }

    Optional<CourseRow> lockCourse(UUID id) {
        return jdbc.sql("select id, title, status, created_by, updated_at, version from courses where id = :id for update")
                .param("id", id).query(CourseRepository::courseRow).optional();
    }

    void updateCourse(UUID id, String title, CourseStatus status, Instant now) {
        jdbc.sql("""
                update courses set title = :title, status = :status, updated_at = :now, version = version + 1,
                    archived_at = case when :status = 'ARCHIVED' then coalesce(archived_at, :now) end
                where id = :id
                """)
                .param("id", id).param("title", title).param("status", status.name()).param("now", Timestamp.from(now))
                .update();
    }

    List<CourseSummary> listCourses(UUID teacherId, CourseStatus status, PageQuery page) {
        return jdbc.sql("""
                select c.id, c.title, c.status, c.updated_at,
                    (select max(edition) from course_versions v where v.course_id = c.id) as latest_edition,
                    (select version from course_versions v where v.course_id = c.id and v.status = 'DRAFT') as draft_revision
                from courses c
                where c.status = :status and (cast(:teacher as uuid) is null
                    or exists (select 1 from course_teachers t where t.course_id = c.id and t.teacher_id = :teacher))
                order by lower(c.title), c.id
                limit :limit offset :offset
                """)
                .param("status", status.name()).param("teacher", teacherId)
                .param("limit", page.size()).param("offset", page.offset())
                .query((rs, row) -> new CourseSummary(rs.getObject("id", UUID.class), rs.getString("title"),
                        CourseStatus.valueOf(rs.getString("status")), (Integer) rs.getObject("latest_edition"),
                        (Long) rs.getObject("draft_revision"), rs.getTimestamp("updated_at").toInstant()))
                .list();
    }

    long countCourses(UUID teacherId, CourseStatus status) {
        return jdbc.sql("""
                select count(*) from courses c where c.status = :status and (cast(:teacher as uuid) is null
                    or exists (select 1 from course_teachers t where t.course_id = c.id and t.teacher_id = :teacher))
                """)
                .param("status", status.name()).param("teacher", teacherId)
                .query(Long.class).single();
    }

    Set<UUID> teachers(UUID courseId) {
        return Set.copyOf(jdbc.sql("select teacher_id from course_teachers where course_id = :id").param("id", courseId).query(UUID.class).list());
    }

    boolean isTeacher(UUID courseId, UUID userId) {
        return jdbc.sql("select count(*) > 0 from course_teachers where course_id = :course and teacher_id = :user")
                .param("course", courseId).param("user", userId).query(Boolean.class).single();
    }

    void replaceTeachers(UUID courseId, Collection<UUID> teacherIds) {
        jdbc.sql("delete from course_teachers where course_id = :id").param("id", courseId).update();
        teacherIds.forEach(teacherId -> jdbc.sql("insert into course_teachers (course_id, teacher_id) values (:course, :teacher)")
                .param("course", courseId).param("teacher", teacherId).update());
    }

    void insertDraft(UUID id, UUID courseId, String title, String description, UUID basedOn, Instant now) {
        jdbc.sql("""
                insert into course_versions (id, course_id, status, title, description, based_on_version_id, created_at, updated_at)
                values (:id, :course, 'DRAFT', :title, :description, :basedOn, :now, :now)
                """)
                .param("id", id).param("course", courseId).param("title", title).param("description", description)
                .param("basedOn", basedOn).param("now", Timestamp.from(now))
                .update();
    }

    Optional<VersionRow> findVersion(UUID id) {
        return jdbc.sql("select " + VERSION_COLUMNS + " from course_versions where id = :id")
                .param("id", id).query(CourseRepository::versionRow).optional();
    }

    Optional<VersionRow> findDraft(UUID courseId) {
        return jdbc.sql("select " + VERSION_COLUMNS + " from course_versions where course_id = :course and status = 'DRAFT'")
                .param("course", courseId).query(CourseRepository::versionRow).optional();
    }

    Optional<VersionRow> lockDraft(UUID courseId) {
        return jdbc.sql("select " + VERSION_COLUMNS + " from course_versions where course_id = :course and status = 'DRAFT' for update")
                .param("course", courseId).query(CourseRepository::versionRow).optional();
    }

    List<VersionRow> editions(UUID courseId) {
        return jdbc.sql("select " + VERSION_COLUMNS + " from course_versions where course_id = :course and status = 'PUBLISHED' order by edition desc")
                .param("course", courseId).query(CourseRepository::versionRow).list();
    }

    /** Updates a draft header if it still has the expected revision; returns false on a stale revision. */
    boolean updateDraft(UUID id, long expectedRevision, String title, String description, Instant now) {
        return jdbc.sql("""
                update course_versions set title = :title, description = :description, updated_at = :now, version = version + 1
                where id = :id and status = 'DRAFT' and version = :expected
                """)
                .param("id", id).param("title", title).param("description", description)
                .param("now", Timestamp.from(now)).param("expected", expectedRevision)
                .update() == 1;
    }

    void publish(UUID id, int edition, UUID publishedBy, Instant now) {
        jdbc.sql("""
                update course_versions set status = 'PUBLISHED', edition = :edition, published_at = :now, published_by = :by,
                    updated_at = :now, version = version + 1
                where id = :id and status = 'DRAFT'
                """)
                .param("id", id).param("edition", edition).param("by", publishedBy).param("now", Timestamp.from(now))
                .update();
    }

    int nextEdition(UUID courseId) {
        return jdbc.sql("select coalesce(max(edition), 0) + 1 from course_versions where course_id = :course")
                .param("course", courseId).query(Integer.class).single();
    }

    private static CourseRow courseRow(ResultSet rs, int row) throws SQLException {
        return new CourseRow(rs.getObject("id", UUID.class), rs.getString("title"), CourseStatus.valueOf(rs.getString("status")),
                rs.getObject("created_by", UUID.class), rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
    }

    private static VersionRow versionRow(ResultSet rs, int row) throws SQLException {
        Timestamp publishedAt = rs.getTimestamp("published_at");
        return new VersionRow(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class), (Integer) rs.getObject("edition"),
                VersionStatus.valueOf(rs.getString("status")), rs.getString("title"), rs.getString("description"),
                rs.getObject("based_on_version_id", UUID.class), publishedAt == null ? null : publishedAt.toInstant(), rs.getLong("version"));
    }
}
