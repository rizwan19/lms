package com.saasresearcher.learning.courses.internal;

import com.saasresearcher.learning.courses.api.CourseLesson;
import com.saasresearcher.learning.courses.api.CourseSection;
import com.saasresearcher.learning.courses.api.LessonVideo;
import com.saasresearcher.learning.courses.api.VideoProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Section/lesson/resource trees of an edition. Only drafts are ever replaced. */
@Repository
class CourseContentRepository {

    private final JdbcClient jdbc;

    CourseContentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<CourseSection> readTree(UUID versionId) {
        Map<UUID, List<UUID>> resources = new LinkedHashMap<>();
        jdbc.sql("""
                select r.lesson_id, r.asset_id from lesson_resources r join lessons l on l.id = r.lesson_id
                where l.course_version_id = :version order by r.lesson_id, r.position
                """)
                .param("version", versionId)
                .query(rs -> {
                    resources.computeIfAbsent(rs.getObject("lesson_id", UUID.class), id -> new ArrayList<>()).add(rs.getObject("asset_id", UUID.class));
                });
        Map<UUID, List<CourseLesson>> lessons = new LinkedHashMap<>();
        jdbc.sql("""
                select id, section_id, title, body_html, video_asset_id, video_provider, video_ref, required
                from lessons where course_version_id = :version order by section_id, position
                """)
                .param("version", versionId)
                .query(rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    String provider = rs.getString("video_provider");
                    UUID videoAsset = rs.getObject("video_asset_id", UUID.class);
                    LessonVideo video = videoAsset == null && provider == null ? null
                            : new LessonVideo(videoAsset, provider == null ? null : VideoProvider.valueOf(provider), rs.getString("video_ref"));
                    lessons.computeIfAbsent(rs.getObject("section_id", UUID.class), section -> new ArrayList<>())
                            .add(new CourseLesson(id, rs.getString("title"), rs.getString("body_html"), video,
                                    resources.getOrDefault(id, List.of()), rs.getBoolean("required")));
                });
        return jdbc.sql("select id, title from sections where course_version_id = :version order by position")
                .param("version", versionId)
                .query((rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new CourseSection(id, rs.getString("title"), lessons.getOrDefault(id, List.of()));
                })
                .list();
    }

    /** Replaces the whole tree in one transaction: deletes first, then inserts in order, so IDs can be kept. */
    void replaceTree(UUID versionId, List<CourseSection> sections) {
        jdbc.sql("delete from lesson_resources where lesson_id in (select id from lessons where course_version_id = :version)")
                .param("version", versionId).update();
        jdbc.sql("delete from lessons where course_version_id = :version").param("version", versionId).update();
        jdbc.sql("delete from sections where course_version_id = :version").param("version", versionId).update();
        for (int s = 0; s < sections.size(); s++) {
            CourseSection section = sections.get(s);
            jdbc.sql("insert into sections (id, course_version_id, title, position) values (:id, :version, :title, :position)")
                    .param("id", section.id()).param("version", versionId).param("title", section.title()).param("position", s)
                    .update();
            for (int l = 0; l < section.lessons().size(); l++) {
                insertLesson(versionId, section.id(), l, section.lessons().get(l));
            }
        }
    }

    List<UUID> requiredExams(UUID versionId) {
        return jdbc.sql("select exam_version_id from course_required_exams where course_version_id = :version order by position")
                .param("version", versionId).query(UUID.class).list();
    }

    void replaceRequiredExams(UUID versionId, List<UUID> examVersionIds) {
        jdbc.sql("delete from course_required_exams where course_version_id = :version").param("version", versionId).update();
        for (int i = 0; i < examVersionIds.size(); i++) {
            jdbc.sql("insert into course_required_exams (course_version_id, exam_version_id, position) values (:version, :exam, :position)")
                    .param("version", versionId).param("exam", examVersionIds.get(i)).param("position", i).update();
        }
    }

    Set<UUID> lessonIds(UUID versionId) {
        return Set.copyOf(jdbc.sql("select id from lessons where course_version_id = :version").param("version", versionId).query(UUID.class).list());
    }

    Set<UUID> sectionIds(UUID versionId) {
        return Set.copyOf(jdbc.sql("select id from sections where course_version_id = :version").param("version", versionId).query(UUID.class).list());
    }

    Set<UUID> versionsUsingAsset(UUID assetId) {
        return Set.copyOf(jdbc.sql("""
                select course_version_id from lessons where video_asset_id = :asset
                union
                select l.course_version_id from lesson_resources r join lessons l on l.id = r.lesson_id where r.asset_id = :asset
                """).param("asset", assetId).query(UUID.class).list());
    }

    boolean teacherUsesAsset(UUID teacherId, UUID assetId) {
        return jdbc.sql("""
                select count(*) > 0 from course_versions v join course_teachers t on t.course_id = v.course_id
                where t.teacher_id = :teacher and v.id in (
                    select course_version_id from lessons where video_asset_id = :asset
                    union
                    select l.course_version_id from lesson_resources r join lessons l on l.id = r.lesson_id where r.asset_id = :asset)
                """).param("teacher", teacherId).param("asset", assetId).query(Boolean.class).single();
    }

    private void insertLesson(UUID versionId, UUID sectionId, int position, CourseLesson lesson) {
        LessonVideo video = lesson.video();
        jdbc.sql("""
                insert into lessons (id, section_id, course_version_id, title, position, body_html, video_asset_id, video_provider, video_ref, required)
                values (:id, :section, :version, :title, :position, :body, :videoAsset, :provider, :ref, :required)
                """)
                .param("id", lesson.id()).param("section", sectionId).param("version", versionId)
                .param("title", lesson.title()).param("position", position).param("body", lesson.bodyHtml())
                .param("videoAsset", video == null ? null : video.assetId())
                .param("provider", video == null || video.provider() == null ? null : video.provider().name())
                .param("ref", video == null ? null : video.providerRef())
                .param("required", lesson.required())
                .update();
        for (int r = 0; r < lesson.resourceAssetIds().size(); r++) {
            jdbc.sql("insert into lesson_resources (lesson_id, position, asset_id) values (:lesson, :position, :asset)")
                    .param("lesson", lesson.id()).param("position", r).param("asset", lesson.resourceAssetIds().get(r))
                    .update();
        }
    }
}
