package com.saasresearcher.learning.reporting;

import static com.saasresearcher.learning.support.ClassFixtures.activeClass;
import static com.saasresearcher.learning.support.ClassFixtures.enroll;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.notifications.infra.JobRunner;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.CompletionFixture;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class ClassReportingIT extends PostgresIntegrationTest {

    @Autowired
    private JobRunner jobs;

    private ApiClient admin;
    private TestUser teacher;

    @BeforeEach
    void teacher() {
        admin = setupAdministrator();
        teacher = createUser(admin, "report.teacher", "report@example.test", "TEACHER");
    }

    @Test
    void lessonPercentCountsOnlyRequiredLessons() {
        JsonNode course = publishedCourse(teacher.session(), "Geography", List.of(lesson("A", "", true), lesson("B", "", true), lesson("C", "", false)));
        List<String> lessons = course.path("sections").get(0).path("lessons").findValuesAsString("id");
        UUID classId = activeClass(teacher.session(), UUID.fromString(course.path("versionId").asString()), "Geography A");
        TestUser student = createUser(admin, "geo.student", null, "STUDENT");
        UUID enrollmentId = enroll(teacher.session(), classId, student.username());
        for (String lesson : List.of(lessons.get(0), lessons.get(2))) {
            student.session().put("/enrollments/" + enrollmentId + "/lessons/" + lesson + "/progress",
                    Map.of("expectedRevision", 0, "completed", true, "resumeSeconds", 0)).expect(200);
        }

        JsonNode row = progress(classId).get(0);
        assertThat(row.path("requiredLessons").asInt()).isEqualTo(2);
        assertThat(row.path("completedRequiredLessons").asInt()).isEqualTo(1);
        assertThat(row.path("lessonPercent").asInt()).isEqualTo(50);

        JsonNode optional = publishedCourse(teacher.session(), "Optional", List.of(lesson("Any", "", false)));
        UUID optionalClass = activeClass(teacher.session(), UUID.fromString(optional.path("versionId").asString()), "Optional A");
        enroll(teacher.session(), optionalClass, student.username());
        assertThat(progress(optionalClass).get(0).path("lessonPercent").isNull()).isTrue();
    }

    @Test
    void neverActiveStudentsBecomeInactiveAfterSevenDaysFromEnrollment() {
        JsonNode course = publishedCourse(teacher.session(), "History", List.of(lesson("A", "", true)));
        String lesson = course.path("sections").get(0).path("lessons").get(0).path("id").asString();
        UUID classId = activeClass(teacher.session(), UUID.fromString(course.path("versionId").asString()), "History A");
        TestUser idle = createUser(admin, "idle.student", null, "STUDENT");
        TestUser busy = createUser(admin, "busy.student", null, "STUDENT");
        enroll(teacher.session(), classId, idle.username());
        UUID busyEnrollment = enroll(teacher.session(), classId, busy.username());

        clock.advance(Duration.ofDays(6));
        assertThat(progress(classId).findValuesAsString("inactive")).containsOnly("false");
        busy.session().put("/enrollments/" + busyEnrollment + "/lessons/" + lesson + "/progress",
                Map.of("expectedRevision", 0, "completed", false, "resumeSeconds", 30)).expect(200);
        clock.advance(Duration.ofDays(2));

        JsonNode rows = progress(classId);
        assertThat(byUsername(rows, "idle.student").path("inactive").asBoolean()).isTrue();
        assertThat(byUsername(rows, "idle.student").path("lastActivityAt").isNull()).isTrue();
        assertThat(byUsername(rows, "busy.student").path("inactive").asBoolean()).isFalse();
    }

    @Test
    void scoresShowOnlyPublishedResultsAndFlagPendingGrading() {
        TestUser student = createUser(admin, "score.student", null, "STUDENT");
        CompletionFixture fixture = CompletionFixture.create(teacher.session(), student.session(), student.username(), 1);
        String attempt = fixture.submitAttempt();

        JsonNode pending = scores(fixture.classId()).get(0).path("scores").get(0);
        assertThat(pending.path("bestEarned").isNull()).isTrue();
        assertThat(pending.path("pendingGrading").asBoolean()).isTrue();
        assertThat(progress(fixture.classId()).get(0).path("pendingGrading").asInt()).isEqualTo(1);

        fixture.publishEssayGrade(attempt, "3");
        JsonNode published = scores(fixture.classId()).get(0).path("scores").get(0);
        assertThat(published.path("bestEarned").decimalValue()).isEqualByComparingTo("4");
        assertThat(published.path("passed").asBoolean()).isTrue();
        assertThat(published.path("publishedRevisionId").isNull()).isFalse();
    }

    @Test
    void exportsAreFormulaSafeUnicodeAndContainNoSecrets() {
        JsonNode course = publishedCourse(teacher.session(), "Civics", List.of(lesson("A", "", true)));
        UUID classId = activeClass(teacher.session(), UUID.fromString(course.path("versionId").asString()), "Civics A");
        ApiResponse created = admin.post("/users", Map.of("username", "formula.student", "displayName", "=HYPERLINK(\"http://evil\")",
                "roles", List.of("STUDENT"), "delivery", "TEMPORARY_PASSWORD")).expect(201);
        String temporary = created.json().path("temporaryCredential").path("temporaryPassword").asString();
        admin.post("/users", Map.of("username", "bangla.student", "displayName", "সুমাইয়া", "roles", List.of("STUDENT"),
                "delivery", "TEMPORARY_PASSWORD")).expect(201);
        enroll(teacher.session(), classId, "formula.student");
        enroll(teacher.session(), classId, "bangla.student");

        String exportId = teacher.session().post("/reports/classes/" + classId + "/export", Map.of("type", "PROGRESS")).expect(202).json().path("id").asString();
        jobs.drain();
        ApiResponse download = teacher.session().get("/reports/exports/" + exportId + "/download").expect(200);
        String csv = download.text();

        assertThat(download.header("Cache-Control")).contains("no-store");
        assertThat(csv).startsWith("student_name,username,").contains("last_activity_Asia/Dhaka", "সুমাইয়া", "'=HYPERLINK");
        assertThat(csv).doesNotContain(temporary).doesNotContainIgnoringCase("password");
        assertThat(teacher.session().get("/notifications").json().path("items").findValuesAsString("title")).contains("Your class report is ready");
    }

    @Test
    void exportAccessIsRecheckedWhenGeneratedAndDownloaded() {
        JsonNode course = publishedCourse(teacher.session(), "Art", List.of(lesson("A", "", true)));
        UUID classId = activeClass(teacher.session(), UUID.fromString(course.path("versionId").asString()), "Art A");
        TestUser replacement = createUser(admin, "replacement", "replacement@example.test", "TEACHER");

        String ready = teacher.session().post("/reports/classes/" + classId + "/export", Map.of("type", "SCORES")).json().path("id").asString();
        jobs.drain();
        assertThat(replacement.session().get("/reports/exports/" + ready + "/download").status()).isEqualTo(404);

        String queued = teacher.session().post("/reports/classes/" + classId + "/export", Map.of("type", "PROGRESS")).json().path("id").asString();
        admin.put("/classes/" + classId + "/teachers", Map.of("teacherIds", List.of(replacement.id()))).expect(200);
        jobs.drain();
        assertThat(jdbc.sql("select status from report_exports where id = :id").param("id", UUID.fromString(queued)).query(String.class).single())
                .isEqualTo("FAILED");
        assertThat(teacher.session().get("/reports/exports/" + ready + "/download").status()).isEqualTo(404);
    }

    @Test
    void unrelatedStaffAndStudentsCannotReadReports() {
        JsonNode course = publishedCourse(teacher.session(), "Music", List.of(lesson("A", "", true)));
        UUID classId = activeClass(teacher.session(), UUID.fromString(course.path("versionId").asString()), "Music A");
        TestUser other = createUser(admin, "other.teacher", "other@example.test", "TEACHER");
        TestUser student = createUser(admin, "music.student", null, "STUDENT");
        enroll(teacher.session(), classId, student.username());
        assertThat(other.session().get("/reports/classes/" + classId + "/progress").status()).isEqualTo(404);
        assertThat(student.session().get("/reports/classes/" + classId + "/scores").status()).isIn(403, 404);
    }

    private JsonNode progress(UUID classId) {
        return teacher.session().get("/reports/classes/" + classId + "/progress").expect(200).json().path("items");
    }

    private JsonNode scores(UUID classId) {
        return teacher.session().get("/reports/classes/" + classId + "/scores").expect(200).json().path("items");
    }

    private static JsonNode byUsername(JsonNode rows, String username) {
        for (JsonNode row : rows) {
            if (row.path("username").asString().equals(username)) {
                return row;
            }
        }
        throw new IllegalArgumentException(username);
    }
}
