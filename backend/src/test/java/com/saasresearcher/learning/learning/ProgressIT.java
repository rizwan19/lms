package com.saasresearcher.learning.learning;

import static com.saasresearcher.learning.support.ClassFixtures.activeClass;
import static com.saasresearcher.learning.support.ClassFixtures.enroll;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ProgressIT extends PostgresIntegrationTest {

    private ApiClient admin;
    private TestUser teacher;
    private TestUser student;
    private UUID classId;
    private UUID enrollmentId;
    private List<String> lessonIds;

    @BeforeEach
    void enrolledStudent() {
        admin = setupAdministrator();
        teacher = createUser(admin, "progress.teacher", "progress@example.test", "TEACHER");
        JsonNode course = publishedCourse(teacher.session(), "Physics",
                List.of(lesson("Speed", "<p>v</p>", true), lesson("Force", "<p>F</p>", true), lesson("Extra", "<p>x</p>", false)));
        lessonIds = course.path("sections").get(0).path("lessons").findValuesAsString("id");
        classId = activeClass(teacher.session(), UUID.fromString(course.path("versionId").asString()), "Physics A");
        student = createUser(admin, "progress.student", null, "STUDENT");
        enrollmentId = enroll(teacher.session(), classId, student.username());
    }

    @Test
    void completionIsExplicitAndOnlyRequiredLessonsCount() {
        JsonNode summary = dashboard().path("summary");
        assertThat(summary.path("required").asInt()).isEqualTo(2);
        assertThat(summary.path("completed").asInt()).isZero();
        assertThat(summary.path("lastActivityAt").isNull()).isTrue();

        save(lessonIds.get(0), 0, false, 300).expect(200);
        assertThat(dashboard().path("summary").path("completed").asInt()).as("resume is not completion").isZero();

        save(lessonIds.get(0), 1, true, 300).expect(200);
        save(lessonIds.get(2), 0, true, 0).expect(200);
        assertThat(dashboard().path("summary").path("completed").asInt()).isEqualTo(1);
        assertThat(dashboard().path("summary").path("lastActivityAt").isNull()).isFalse();

        save(lessonIds.get(0), 2, false, 300).expect(200);
        assertThat(dashboard().path("summary").path("completed").asInt()).isZero();
        assertThat(progressObserver.changed()).hasSize(4).containsOnly(enrollmentId);
    }

    @Test
    void courseWithoutRequiredLessonsReportsZeroRequired() {
        JsonNode optional = publishedCourse(teacher.session(), "Reading club", List.of(lesson("Any", "", false)));
        UUID club = activeClass(teacher.session(), UUID.fromString(optional.path("versionId").asString()), "Club");
        enroll(teacher.session(), club, student.username());
        JsonNode entry = student.session().get("/me/learning").json().findParents("className").stream()
                .filter(node -> node.path("className").asString().equals("Club")).findFirst().orElseThrow();
        assertThat(entry.path("summary").path("required").asInt()).isZero();
    }

    @Test
    void resumeIsMonotonicAndStaleRevisionsConflict() {
        JsonNode first = save(lessonIds.get(1), 0, false, 120).expect(200).json();
        assertThat(first.path("revision").asLong()).isEqualTo(1);

        JsonNode older = save(lessonIds.get(1), 1, false, 60).expect(200).json();
        assertThat(older.path("resumeSeconds").asLong()).isEqualTo(120);

        ApiResponse retry = save(lessonIds.get(1), 1, false, 200);
        assertThat(retry.status()).isEqualTo(409);
        assertThat(retry.code()).isEqualTo("PROGRESS_REVISION_CONFLICT");
        assertThat(save(lessonIds.get(1), 0, false, 10).status()).isEqualTo(409);
        assertThat(jdbc.sql("select count(*) from lesson_progress").query(Long.class).single()).isEqualTo(1);

        JsonNode player = student.session().get("/me/enrollments/" + enrollmentId).expect(200).json();
        assertThat(player.path("progress").get(0).path("resumeSeconds").asLong()).isEqualTo(120);
        assertThat(player.path("progress").get(0).path("revision").asLong()).isEqualTo(2);
    }

    @Test
    void concurrentSavesWithTheSameRevisionApplyOnce() throws Exception {
        ApiClient secondTab = login(student.username(), PASSWORD);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (ApiClient tab : List.of(student.session(), secondTab)) {
                results.add(pool.submit(() -> {
                    barrier.await();
                    return tab.put(progressPath(lessonIds.get(0)), Map.of("expectedRevision", 0, "completed", true, "resumeSeconds", 0)).status();
                }));
            }
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.sql("select count(*) from lesson_progress").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void lessonsOfOtherEditionsAndOtherStudentsAreRejected() {
        JsonNode other = publishedCourse(teacher.session(), "Chemistry", List.of(lesson("Atoms", "", true)));
        String foreignLesson = other.path("sections").get(0).path("lessons").get(0).path("id").asString();
        assertThat(save(foreignLesson, 0, true, 0).status()).isEqualTo(404);

        TestUser intruder = createUser(admin, "intruder", null, "STUDENT");
        assertThat(intruder.session().put(progressPath(lessonIds.get(0)), Map.of("expectedRevision", 0, "completed", true, "resumeSeconds", 0)).status())
                .isEqualTo(404);
        assertThat(intruder.session().get("/me/enrollments/" + enrollmentId).status()).isEqualTo(404);
        assertThat(teacher.session().get("/me/learning").json().size()).isZero();
    }

    @Test
    void suspendedStudentsKeepTheirRecordButCannotStudy() {
        save(lessonIds.get(0), 0, true, 0).expect(200);
        teacher.session().put("/classes/" + classId + "/enrollments/" + enrollmentId, Map.of("status", "SUSPENDED")).expect(200);

        ApiResponse write = save(lessonIds.get(0), 1, false, 0);
        assertThat(write.status()).isEqualTo(403);
        assertThat(write.code()).isEqualTo("ENROLLMENT_INACTIVE");
        assertThat(student.session().get("/me/enrollments/" + enrollmentId).status()).isEqualTo(403);
        JsonNode entry = dashboard();
        assertThat(entry.path("accessOpen").asBoolean()).isFalse();
        assertThat(entry.path("summary").path("completed").asInt()).isEqualTo(1);
    }

    private JsonNode dashboard() {
        return student.session().get("/me/learning").expect(200).json().get(0);
    }

    private ApiResponse save(String lessonId, long expectedRevision, boolean completed, long resumeSeconds) {
        return student.session().put(progressPath(lessonId),
                Map.of("expectedRevision", expectedRevision, "completed", completed, "resumeSeconds", resumeSeconds));
    }

    private String progressPath(String lessonId) {
        return "/enrollments/" + enrollmentId + "/lessons/" + lessonId + "/progress";
    }
}
