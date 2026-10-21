package com.saasresearcher.learning.certificates;

import static com.saasresearcher.learning.support.ClassFixtures.activeClass;
import static com.saasresearcher.learning.support.ClassFixtures.enroll;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.completion.api.CompletionService;
import com.saasresearcher.learning.notifications.infra.JobRunner;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.CompletionFixture;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class CertificateLifecycleIT extends PostgresIntegrationTest {

    @Autowired
    private JobRunner jobs;

    @Autowired
    private CompletionService completions;

    private ApiClient admin;
    private TestUser teacher;
    private TestUser student;

    @BeforeEach
    void users() {
        admin = setupAdministrator();
        teacher = createUser(admin, "cert.teacher", "cert@example.test", "TEACHER");
        student = createUser(admin, "cert.student", null, "STUDENT");
    }

    @Test
    void completionIssuesOneRenderedCertificateFromItsSnapshot() throws IOException {
        Completed completed = complete(student);
        JsonNode certificate = only(student.session());
        assertThat(certificate.path("status").asString()).isEqualTo("PENDING_RENDER");
        queueRender(certificate.path("id").asString());
        jobs.drain();

        JsonNode issued = only(student.session());
        assertThat(issued.path("status").asString()).isEqualTo("ISSUED");
        assertThat(issued.toString()).doesNotContain("verificationToken");
        String text = pdfText(student.session(), issued.path("id").asString());
        assertThat(text).contains("Certificate of Completion", "User cert.student", completed.courseTitle(), "Test School");
        assertThat(student.session().get("/notifications").json().path("items").findValuesAsString("title")).contains("Your certificate is ready");

        long revision = admin.get("/institution").json().path("revision").asLong();
        admin.put("/institution", Map.of("name", "Renamed School", "timezone", "Asia/Dhaka", "certificateVerification", false,
                "verificationShowsStudent", false, "expectedRevision", revision)).expect(200);
        assertThat(pdfText(student.session(), issued.path("id").asString())).contains("Test School").doesNotContain("Renamed School");
    }

    @Test
    void crashAfterUploadRetryReusesTheSameCertificateAndFile() {
        complete(student);
        jobs.drain();
        String id = only(student.session()).path("id").asString();
        jdbc.sql("update certificates set status = 'PENDING_RENDER' where id = :id").param("id", UUID.fromString(id)).update();
        queueRender(id);
        jobs.drain();
        assertThat(only(student.session()).path("status").asString()).isEqualTo("ISSUED");
        assertThat(jdbc.sql("select count(*) from certificates").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select storage_key from certificates").query(String.class).single()).isEqualTo("certificates/" + id);
    }

    @Test
    void revocationBeforeRenderingWins() {
        Completed completed = complete(student);
        String id = only(student.session()).path("id").asString();
        teacher.session().post("/certificates/" + id + "/revoke", Map.of("reason", "Issued in error")).expect(200);
        jobs.drain();

        assertThat(only(student.session()).path("status").asString()).isEqualTo("REVOKED");
        assertThat(student.session().get("/certificates/" + id + "/download").status()).isEqualTo(409);
        assertThat(teacher.session().get("/certificates?classId=" + completed.classId()).json().get(0).path("revokedReason").asString())
                .isEqualTo("Issued in error");
    }

    @Test
    void failingGradeCorrectionRevokesAndOnlyAnAdministratorReinstates() throws IOException {
        CompletionFixture fixture = CompletionFixture.create(teacher.session(), student.session(), student.username(), 1);
        fixture.completeLesson(0, true);
        fixture.completeLesson(1, true);
        String attempt = fixture.submitAttempt();
        fixture.publishEssayGrade(attempt, "3");
        jobs.drain();
        String id = only(student.session()).path("id").asString();
        assertThat(only(student.session()).path("status").asString()).isEqualTo("ISSUED");

        fixture.publishEssayGrade(attempt, "0");
        jobs.drain();
        JsonNode revoked = only(student.session());
        assertThat(revoked.path("status").asString()).isEqualTo("REVOKED");
        assertThat(revoked.path("revokedReason").asString()).contains("grade correction");
        assertThat(admin.post("/certificates/" + id + "/reinstate", Map.of("reason", "Correction reversed")).code()).isEqualTo("NOT_ELIGIBLE");

        fixture.publishEssayGrade(attempt, "4");
        jobs.drain();
        assertThat(only(student.session()).path("status").asString()).as("no silent un-revoke").isEqualTo("REVOKED");
        assertThat(teacher.session().post("/certificates/" + id + "/reinstate", Map.of("reason", "Please")).status()).isEqualTo(403);
        assertThat(admin.post("/certificates/" + id + "/reinstate", Map.of("reason", "")).status()).isEqualTo(400);
        admin.post("/certificates/" + id + "/reinstate", Map.of("reason", "Correction reversed")).expect(200);
        jobs.drain();
        assertThat(only(student.session()).path("status").asString()).isEqualTo("ISSUED");
        assertThat(only(student.session()).path("id").asString()).isEqualTo(id);
        assertThat(pdfText(student.session(), id)).contains("User cert.student");
        assertThat(jdbc.sql("select count(*) from audit_events where action in ('certificate.revoked', 'certificate.reinstated')").query(Long.class).single())
                .isEqualTo(2);
    }

    @Test
    void verificationIsDisabledByDefaultAndMinimalWhenEnabled() {
        complete(student);
        jobs.drain();
        assertThat(jdbc.sql("select verification_hash from certificates").query(String.class).optional()).isEmpty();
        assertThat(client().get("/public/certificates/anything").status()).isEqualTo(404);

        long revision = admin.get("/institution").json().path("revision").asLong();
        admin.put("/institution", Map.of("name", "Test School", "timezone", "Asia/Dhaka", "certificateVerification", true,
                "verificationShowsStudent", false, "expectedRevision", revision)).expect(200);
        TestUser second = createUser(admin, "second.student", "second@example.test", "STUDENT");
        complete(second);
        jobs.drain();
        String token = jdbc.sql("select snapshot->>'verificationToken' from certificates where verification_hash is not null").query(String.class).single();
        assertThat(token).hasSizeGreaterThanOrEqualTo(32);

        JsonNode verified = client().get("/public/certificates/" + token).expect(200).json();
        assertThat(verified.path("status").asString()).isEqualTo("VALID");
        assertThat(verified.path("institution").asString()).isEqualTo("Test School");
        assertThat(verified.path("student").isNull()).isTrue();
        assertThat(verified.toString()).doesNotContain("second@example.test", "second.student");
        assertThat(client().get("/public/certificates/" + token.substring(1) + "x").status()).isEqualTo(404);

        String id = only(second.session()).path("id").asString();
        admin.post("/certificates/" + id + "/revoke", Map.of("reason", "Fraud")).expect(200);
        JsonNode revoked = client().get("/public/certificates/" + token).expect(200).json();
        assertThat(revoked.path("status").asString()).isEqualTo("REVOKED");
        assertThat(revoked.path("course").isNull()).isTrue();

        revision = admin.get("/institution").json().path("revision").asLong();
        admin.put("/institution", Map.of("name", "Test School", "timezone", "Asia/Dhaka", "certificateVerification", false,
                "verificationShowsStudent", false, "expectedRevision", revision)).expect(200);
        assertThat(client().get("/public/certificates/" + token).status()).isEqualTo(404);
    }

    @Test
    void downloadsAreLimitedToOwnersAndTheirTeachersAndSurviveWithdrawal() {
        Completed completed = complete(student);
        jobs.drain();
        String id = only(student.session()).path("id").asString();
        TestUser otherStudent = createUser(admin, "nosy.student", null, "STUDENT");
        TestUser otherTeacher = createUser(admin, "nosy.teacher", "nosy@example.test", "TEACHER");
        assertThat(otherStudent.session().get("/certificates/" + id + "/download").status()).isEqualTo(404);
        assertThat(otherTeacher.session().get("/certificates/" + id + "/download").status()).isEqualTo(404);
        assertThat(teacher.session().get("/certificates/" + id + "/download").status()).isEqualTo(200);

        teacher.session().put("/classes/" + completed.classId() + "/enrollments/" + completed.enrollmentId(), Map.of("status", "WITHDRAWN")).expect(200);
        ApiResponse download = student.session().get("/certificates/" + id + "/download");
        assertThat(download.status()).isEqualTo(200);
        assertThat(download.header("Cache-Control")).contains("no-store");
        assertThat(new String(download.bytes(), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void namesTheFontCannotDrawFailExplicitlyAndCanBeRetried() {
        ApiResponse created = admin.post("/users", Map.of("username", "bengali.student", "displayName", "রহিম উদ্দিন", "roles", List.of("STUDENT"),
                "delivery", "TEMPORARY_PASSWORD")).expect(201);
        String temporary = created.json().path("temporaryCredential").path("temporaryPassword").asString();
        ApiClient session = login("bengali.student", temporary);
        session.put("/auth/me/password", Map.of("currentPassword", temporary, "newPassword", PASSWORD)).expect(204);
        TestUser bengali = new TestUser(UUID.fromString(created.json().path("account").path("id").asString()), "bengali.student", session);
        complete(bengali);
        jobs.drain();

        JsonNode failed = only(session);
        assertThat(failed.path("status").asString()).isEqualTo("RENDER_FAILED");
        assertThat(failed.path("renderError").asString()).contains("student name", "cannot display");
        teacher.session().post("/certificates/" + failed.path("id").asString() + "/retry", null).expect(200);
        jobs.drain();
        assertThat(only(session).path("status").asString()).isEqualTo("RENDER_FAILED");
    }

    private record Completed(UUID classId, UUID enrollmentId, String courseTitle) {
    }

    /** Enrolls the student in a course without requirements and reconciles, which achieves completion at once. */
    private Completed complete(TestUser learner) {
        String title = "Certified course " + UUID.randomUUID().toString().substring(0, 8);
        JsonNode course = publishedCourse(teacher.session(), title, List.of(lesson("Optional", "", false)));
        UUID classId = activeClass(teacher.session(), UUID.fromString(course.path("versionId").asString()), "Certificates");
        UUID enrollmentId = enroll(teacher.session(), classId, learner.username());
        completions.reconcile(enrollmentId);
        return new Completed(classId, enrollmentId, title);
    }

    /** A duplicate render job, due on the test clock. */
    private void queueRender(String certificateId) {
        jdbc.sql("insert into background_jobs (id, type, aggregate_id, payload, status, attempts, available_at, created_at, updated_at) "
                        + "values (gen_random_uuid(), 'certificates.render', :id, '{}', 'PENDING', 0, :now, :now, :now)")
                .param("id", UUID.fromString(certificateId)).param("now", java.sql.Timestamp.from(clock.instant())).update();
    }

    private static JsonNode only(ApiClient session) {
        JsonNode certificates = session.get("/me/certificates").expect(200).json();
        assertThat(certificates).hasSize(1);
        return certificates.get(0);
    }

    private static String pdfText(ApiClient session, String id) throws IOException {
        try (PDDocument document = Loader.loadPDF(session.get("/certificates/" + id + "/download").expect(200).bytes())) {
            return new PDFTextStripper().getText(document);
        }
    }
}
