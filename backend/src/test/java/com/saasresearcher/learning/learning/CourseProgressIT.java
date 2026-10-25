package com.saasresearcher.learning.learning;

import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.EnrollmentFixtures;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
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

/** Lesson progress on direct course enrollments, always against the enrollment's pinned edition. */
class CourseProgressIT extends PostgresIntegrationTest {

    private TestUser owner;
    private TestUser author;
    private TestUser learner;
    private UUID courseId;
    private UUID enrollmentId;
    private List<String> lessonIds;

    @BeforeEach
    void enrolledLearner() {
        owner = setupOwner();
        author = approvedTeacher("author", owner);
        JsonNode course = publishedCourse(author.session(), "Physics",
                List.of(lesson("Speed", "<p>v</p>", true), lesson("Force", "<p>F</p>", true), lesson("Extra", "<p>x</p>", false)));
        courseId = UUID.fromString(course.path("courseId").asString());
        lessonIds = course.path("sections").get(0).path("lessons").findValuesAsString("id");
        learner = registerStudent("learner");
        enrollmentId = EnrollmentFixtures.enroll(author.session(), learner.session(), courseId);
    }

    @Test
    void completionIsExplicitAndOnlyRequiredLessonsCount() {
        JsonNode summary = dashboard().path("summary");
        assertThat(summary.path("required").asInt()).isEqualTo(2);
        assertThat(summary.path("completed").asInt()).isZero();
        assertThat(summary.path("lastActivityAt").isNull()).isTrue();

        save(lessonIds.get(0), 0, false, 300).expect(200);
        assertThat(dashboard().path("summary").path("completed").asInt()).as("resume is not completion").isZero();
        Instant before = clock.instant();
        clock.advance(Duration.ofMinutes(5));
        save(lessonIds.get(0), 1, true, 300).expect(200);
        save(lessonIds.get(2), 0, true, 0).expect(200);
        assertThat(dashboard().path("summary").path("completed").asInt()).isEqualTo(1);
        assertThat(Instant.parse(dashboard().path("summary").path("lastActivityAt").asString())).isAfter(before);

        save(lessonIds.get(0), 2, false, 300).expect(200);
        assertThat(dashboard().path("summary").path("completed").asInt()).isZero();
        assertThat(progressObserver.changed()).as("only completion changes notify").hasSize(3).containsOnly(enrollmentId);
        assertThat(jdbc.sql("select count(*) from lesson_progress where course_id = :course").param("course", courseId).query(Long.class).single())
                .isEqualTo(2);
    }

    @Test
    void resumeIsMonotonicAndStaleRevisionsConflict() {
        assertThat(save(lessonIds.get(1), 0, false, 120).expect(200).json().path("revision").asLong()).isEqualTo(1);
        assertThat(save(lessonIds.get(1), 1, false, 60).expect(200).json().path("resumeSeconds").asLong()).isEqualTo(120);

        ApiResponse retry = save(lessonIds.get(1), 1, false, 200);
        assertThat(retry.status()).isEqualTo(409);
        assertThat(retry.code()).isEqualTo("PROGRESS_REVISION_CONFLICT");
        assertThat(save(lessonIds.get(1), 0, false, 10).status()).isEqualTo(409);

        JsonNode player = learner.session().get("/me/enrollments/" + enrollmentId + "/content").expect(200).json();
        assertThat(player.path("progress").get(0).path("resumeSeconds").asLong()).isEqualTo(120);
        assertThat(player.path("progress").get(0).path("revision").asLong()).isEqualTo(2);
    }

    @Test
    void concurrentSavesWithTheSameRevisionApplyOnce() throws Exception {
        ApiClient secondTab = login(learner.username(), PASSWORD);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (ApiClient tab : List.of(learner.session(), secondTab)) {
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
    void lessonsOfOtherEditionsAndOtherLearnersAreRejected() {
        JsonNode other = publishedCourse(author.session(), "Chemistry", List.of(lesson("Atoms", "", true)));
        String foreignLesson = other.path("sections").get(0).path("lessons").get(0).path("id").asString();
        assertThat(save(foreignLesson, 0, true, 0).status()).isEqualTo(404);

        TestUser intruder = registerStudent("intruder");
        assertThat(intruder.session().put(progressPath(lessonIds.get(0)), Map.of("expectedRevision", 0, "completed", true, "resumeSeconds", 0)).status())
                .isEqualTo(404);
        assertThat(intruder.session().get("/me/enrollments/" + enrollmentId + "/content").status()).isEqualTo(404);
        assertThat(author.session().get("/me/enrollments/" + enrollmentId + "/content").status()).as("authors preview instead").isEqualTo(404);
        assertThat(owner.session().get("/me/learning").json()).isEmpty();
    }

    @Test
    void teachersLearnThroughTheSameRoutes() {
        TestUser teacher = approvedTeacher("teacher.learner", owner);
        UUID own = EnrollmentFixtures.enroll(author.session(), teacher.session(), courseId);
        teacher.session().put("/enrollments/" + own + "/lessons/" + lessonIds.get(0) + "/progress",
                Map.of("expectedRevision", 0, "completed", true, "resumeSeconds", 0)).expect(200);
        assertThat(teacher.session().get("/me/learning").json().get(0).path("summary").path("completed").asInt()).isEqualTo(1);
    }

    @Test
    void suspendedLearnersKeepTheirRecordButCannotStudy() {
        save(lessonIds.get(0), 0, true, 0).expect(200);
        long revision = author.session().get("/courses/" + courseId + "/enrollments").json().path("items").get(0).path("revision").asLong();
        author.session().put("/enrollments/" + enrollmentId + "/status", Map.of("status", "SUSPENDED", "expectedRevision", revision)).expect(200);

        ApiResponse write = save(lessonIds.get(0), 1, false, 0);
        assertThat(write.status()).isEqualTo(403);
        assertThat(write.code()).isEqualTo("ENROLLMENT_INACTIVE");
        assertThat(learner.session().get("/me/enrollments/" + enrollmentId + "/content").status()).isEqualTo(403);
        JsonNode entry = dashboard();
        assertThat(entry.path("accessOpen").asBoolean()).isFalse();
        assertThat(entry.path("summary").path("completed").asInt()).isEqualTo(1);
    }

    @Test
    void archivedCoursesKeepServingExistingLearners() {
        author.session().post("/courses/" + courseId + "/archive", null).expect(204);
        save(lessonIds.get(0), 0, true, 0).expect(200);
        assertThat(learner.session().get("/me/enrollments/" + enrollmentId + "/content").status()).isEqualTo(200);
    }

    private JsonNode dashboard() {
        return learner.session().get("/me/learning").expect(200).json().get(0);
    }

    private ApiResponse save(String lessonId, long expectedRevision, boolean completed, long resumeSeconds) {
        return learner.session().put(progressPath(lessonId),
                Map.of("expectedRevision", expectedRevision, "completed", completed, "resumeSeconds", resumeSeconds));
    }

    private String progressPath(String lessonId) {
        return "/enrollments/" + enrollmentId + "/lessons/" + lessonId + "/progress";
    }
}
