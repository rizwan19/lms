package com.saasresearcher.learning;

import static com.saasresearcher.learning.support.ClassFixtures.activeClass;
import static com.saasresearcher.learning.support.ClassFixtures.enroll;
import static com.saasresearcher.learning.support.CourseFixtures.createCourse;
import static com.saasresearcher.learning.support.CourseFixtures.draft;
import static com.saasresearcher.learning.support.CourseFixtures.draftRevision;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static com.saasresearcher.learning.support.ExamFixtures.publishedExam;
import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static com.saasresearcher.learning.support.ExamFixtures.written;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.notifications.internal.JobRunner;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;

/**
 * The release journey across all three roles: setup, accounts, publication, class, enrollment, study, a mixed exam
 * interrupted by an application restart, grading, results, completion, certificate and public verification; then
 * teacher reassignment, a failing grade correction and withdrawal. Asserts history persists and nothing leaks.
 */
class ReleaseJourneyIT extends PostgresIntegrationTest {

    @Autowired
    private JobRunner jobs;

    @Autowired
    private PostgreSQLContainer database;

    @Test
    void completeLearningJourney() {
        ApiClient admin = setupAdministrator();
        long revision = admin.get("/institution").json().path("revision").asLong();
        admin.put("/institution", Map.of("name", "Journey School", "timezone", "Asia/Dhaka", "certificateVerification", true,
                "verificationShowsStudent", true, "expectedRevision", revision)).expect(200);
        TestUser teacher = createUser(admin, "journey.teacher", "journey@example.test", "TEACHER");
        TestUser substitute = createUser(admin, "journey.substitute", "substitute@example.test", "TEACHER");
        TestUser student = createUser(teacher.session(), "journey.student", null, "STUDENT");
        TestUser classmate = createUser(teacher.session(), "journey.classmate", null, "STUDENT");

        UUID courseId = createCourse(teacher.session(), "Journey course");
        JsonNode exam = publishedExam(teacher.session(), courseId, "Final", "50", List.of(singleChoice("Pick A", "1", 0), written("Essay", "4")));
        teacher.session().put("/courses/" + courseId + "/draft", draft(draftRevision(teacher.session(), courseId), "Journey course",
                List.of(section("Unit", List.of(lesson("Read", "<p>Read</p>", true), lesson("Extra", "", false)))),
                List.of(exam.path("versionId").asString()))).expect(200);
        JsonNode edition = publish(teacher.session(), courseId);
        UUID classId = activeClass(teacher.session(), UUID.fromString(edition.path("versionId").asString()), "Journey class");
        UUID enrollmentId = enroll(teacher.session(), classId, student.username());
        enroll(teacher.session(), classId, classmate.username());

        String lessonId = edition.path("sections").get(0).path("lessons").get(0).path("id").asString();
        student.session().put("/enrollments/" + enrollmentId + "/lessons/" + lessonId + "/progress",
                Map.of("expectedRevision", 0, "completed", true, "resumeSeconds", 0)).expect(200);

        String assignment = teacher.session().get("/exam-assignments?classId=" + classId).json().get(0).path("id").asString();
        String attempt = student.session().post("/exam-assignments/" + assignment + "/attempts", null).expect(200).json().path("id").asString();
        JsonNode single = question(exam, "Pick A");
        student.session().put("/attempts/" + attempt + "/answers/" + single.path("id").asString(),
                Map.of("expectedRevision", 0, "selectedOptionIds", List.of(single.path("options").get(0).path("id").asString()))).expect(200);

        // A second application instance on the same database stands in for a restart mid-attempt.
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(LearningPortalApplication.class).profiles("test").run(
                "--spring.datasource.url=" + database.getJdbcUrl(), "--spring.datasource.username=" + database.getUsername(),
                "--spring.datasource.password=" + database.getPassword(), "--server.port=0", "--management.server.port=0")) {
            ApiClient reconnected = new ApiClient("http://localhost:" + restarted.getEnvironment().getProperty("local.server.port") + "/api/v1");
            reconnected.get("/auth/csrf");
            reconnected.post("/auth/login", Map.of("username", student.username(), "password", PASSWORD)).expect(200);
            reconnected.get("/auth/csrf");
            JsonNode resumed = reconnected.get("/attempts/" + attempt).expect(200).json();
            assertThat(resumed.path("answers").get(0).path("revision").asLong()).isEqualTo(1);
            reconnected.put("/attempts/" + attempt + "/answers/" + question(exam, "Essay").path("id").asString(),
                    Map.of("expectedRevision", 0, "writtenText", "Saved after the restart")).expect(200);
            reconnected.post("/attempts/" + attempt + "/submit", null).expect(200);
        }

        assertThat(student.session().get("/me/results").json().get(0).path("state").asString()).isEqualTo("PENDING");
        assertThat(classmate.session().get("/attempts/" + attempt).status()).isEqualTo(404);

        admin.put("/classes/" + classId + "/teachers", Map.of("teacherIds", List.of(substitute.id()))).expect(200);
        assertThat(teacher.session().get("/grading/attempts/" + attempt).status()).as("reassigned teacher loses access").isEqualTo(404);
        grade(substitute.session(), attempt, exam, "3");
        jobs.drain();

        JsonNode result = student.session().get("/me/results").json().get(0);
        assertThat(result.path("earned").decimalValue()).isEqualByComparingTo("4");
        assertThat(result.path("passed").asBoolean()).isTrue();
        JsonNode certificate = student.session().get("/me/certificates").expect(200).json().get(0);
        assertThat(certificate.path("status").asString()).isEqualTo("ISSUED");
        String token = jdbc.sql("select snapshot->>'verificationToken' from certificates").query(String.class).single();
        JsonNode verified = client().get("/public/certificates/" + token).expect(200).json();
        assertThat(verified.path("status").asString()).isEqualTo("VALID");
        assertThat(verified.path("student").asString()).isEqualTo("User journey.student");

        grade(substitute.session(), attempt, exam, "0");
        jobs.drain();
        assertThat(student.session().get("/me/certificates").json().get(0).path("status").asString()).isEqualTo("REVOKED");
        assertThat(client().get("/public/certificates/" + token).json().path("status").asString()).isEqualTo("REVOKED");
        grade(substitute.session(), attempt, exam, "4");
        jobs.drain();
        admin.post("/certificates/" + certificate.path("id").asString() + "/reinstate", Map.of("reason", "Correction reversed")).expect(200);
        jobs.drain();

        substitute.session().put("/classes/" + classId + "/enrollments/" + enrollmentId, Map.of("status", "WITHDRAWN")).expect(200);
        assertThat(student.session().get("/me/enrollments/" + enrollmentId).status()).isEqualTo(403);
        assertThat(student.session().get("/me/learning").json().get(0).path("summary").path("completed").asInt()).isEqualTo(1);
        assertThat(student.session().get("/me/results").json().get(0).path("earned").decimalValue()).isEqualByComparingTo("5");
        assertThat(student.session().get("/certificates/" + certificate.path("id").asString() + "/download").status()).isEqualTo(200);
        assertThat(classmate.session().get("/certificates/" + certificate.path("id").asString() + "/download").status()).isEqualTo(404);
        assertThat(classmate.session().get("/me/results").json()).isEmpty();
        assertThat(jdbc.sql("select count(*) from audit_events where action in ('grade.published', 'certificate.revoked', 'certificate.reinstated')")
                .query(Long.class).single()).isGreaterThanOrEqualTo(5);
    }

    private static void grade(ApiClient teacher, String attempt, JsonNode exam, String points) {
        String base = "/grading/attempts/" + attempt;
        if (teacher.get(base).expect(200).json().path("revision").path("status").asString().equals("PUBLISHED")) {
            teacher.post(base + "/regrade", Map.of("reason", "Review")).expect(200);
        }
        teacher.put(base + "/answers/" + question(exam, "Essay").path("id").asString(),
                Map.of("expectedRevision", version(teacher, base), "points", points, "feedback", "Reviewed")).expect(200);
        teacher.post(base + "/finalize", Map.of("expectedRevision", version(teacher, base))).expect(200);
        teacher.post(base + "/publish", Map.of("expectedRevision", version(teacher, base))).expect(200);
    }

    private static long version(ApiClient teacher, String base) {
        return teacher.get(base).json().path("revision").path("version").asLong();
    }

    private static JsonNode question(JsonNode exam, String prompt) {
        for (JsonNode question : exam.path("questions")) {
            if (question.path("prompt").asString().equals(prompt)) {
                return question;
            }
        }
        throw new IllegalArgumentException(prompt);
    }
}
