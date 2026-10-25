package com.saasresearcher.learning.certificates;

import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.completion.api.CompletionService;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.CompletionFixture;
import com.saasresearcher.learning.support.EnrollmentFixtures;
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

/** One certificate per completion, readable by the learner and the course author only. */
class CourseCertificateIT extends PostgresIntegrationTest {

    @Autowired
    private CompletionService completions;

    private TestUser owner;
    private TestUser author;
    private TestUser learner;

    @BeforeEach
    void users() {
        owner = setupOwner();
        author = approvedTeacher("cert.author", owner);
        learner = registerStudent("cert.learner");
    }

    @Test
    void completionIssuesOneRenderedCertificateFromItsSnapshot() throws IOException {
        Completed completed = complete(learner);
        JsonNode certificate = only(learner.session());
        assertThat(certificate.path("status").asString()).isEqualTo("PENDING_RENDER");
        runJobs();

        JsonNode issued = only(learner.session());
        assertThat(issued.path("status").asString()).isEqualTo("ISSUED");
        assertThat(issued.path("authorName").asString()).isEqualTo("User cert.author");
        assertThat(issued.toString()).doesNotContain("verificationToken");
        String text = pdfText(learner.session(), issued.path("id").asString());
        assertThat(text).contains("Certificate of Completion", "User cert.learner", completed.courseTitle(), "Test School", "Course author: User cert.author");
        assertThat(learner.session().get("/notifications").json().path("items").findValuesAsString("title")).contains("Your certificate is ready");

        long revision = owner.session().get("/workspace").json().path("revision").asLong();
        owner.session().put("/workspace", Map.of("name", "Renamed School", "timezone", "Asia/Dhaka", "certificateVerification", false,
                "verificationShowsStudent", false, "expectedRevision", revision)).expect(200);
        assertThat(pdfText(learner.session(), issued.path("id").asString())).contains("Test School").doesNotContain("Renamed School");
    }

    @Test
    void downloadsAreLimitedToTheLearnerAndTheCourseAuthorAndSurviveWithdrawal() {
        Completed completed = complete(learner);
        runJobs();
        String id = only(learner.session()).path("id").asString();
        TestUser otherLearner = registerStudent("nosy.learner");
        TestUser otherTeacher = approvedTeacher("nosy.teacher", owner);
        for (ApiClient outsider : List.of(otherLearner.session(), otherTeacher.session(), owner.session())) {
            assertThat(outsider.get("/certificates/" + id + "/download").status()).isEqualTo(404);
            assertThat(outsider.post("/certificates/" + id + "/revoke", Map.of("reason", "x")).status()).isEqualTo(404);
        }
        assertThat(otherTeacher.session().get("/courses/" + completed.courseId() + "/certificates").status()).isEqualTo(404);
        assertThat(author.session().get("/certificates/" + id + "/download").status()).isEqualTo(200);
        assertThat(author.session().get("/courses/" + completed.courseId() + "/certificates").json()).hasSize(1);

        learner.session().post("/me/enrollments/" + completed.enrollmentId() + "/withdraw", null).expect(204);
        ApiResponse download = learner.session().get("/certificates/" + id + "/download");
        assertThat(download.status()).isEqualTo(200);
        assertThat(download.header("Cache-Control")).contains("no-store");
        assertThat(new String(download.bytes(), 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void revocationBeforeRenderingWins() {
        Completed completed = complete(learner);
        String id = only(learner.session()).path("id").asString();
        author.session().post("/certificates/" + id + "/revoke", Map.of("reason", "Issued in error")).expect(200);
        runJobs();
        assertThat(only(learner.session()).path("status").asString()).isEqualTo("REVOKED");
        assertThat(learner.session().get("/certificates/" + id + "/download").status()).isEqualTo(409);
        assertThat(author.session().get("/courses/" + completed.courseId() + "/certificates").json().get(0).path("revokedReason").asString())
                .isEqualTo("Issued in error");
    }

    @Test
    void failingGradeCorrectionRevokesAndOnlyTheApprovedAuthorReinstates() throws IOException {
        CompletionFixture fixture = CompletionFixture.create(author.session(), learner.session(), 1);
        fixture.completeLesson(0, true);
        fixture.completeLesson(1, true);
        String attempt = fixture.submitAttempt();
        fixture.publishEssayGrade(attempt, "3");
        runJobs();
        String id = only(learner.session()).path("id").asString();
        assertThat(only(learner.session()).path("status").asString()).isEqualTo("ISSUED");

        fixture.publishEssayGrade(attempt, "0");
        runJobs();
        JsonNode revoked = only(learner.session());
        assertThat(revoked.path("status").asString()).isEqualTo("REVOKED");
        assertThat(revoked.path("revokedReason").asString()).contains("grade correction");
        assertThat(author.session().post("/certificates/" + id + "/reinstate", Map.of("reason", "Correction reversed")).code()).isEqualTo("NOT_ELIGIBLE");

        fixture.publishEssayGrade(attempt, "4");
        runJobs();
        assertThat(only(learner.session()).path("status").asString()).as("no silent un-revoke").isEqualTo("REVOKED");
        assertThat(owner.session().post("/certificates/" + id + "/reinstate", Map.of("reason", "Please")).status()).isEqualTo(404);
        assertThat(author.session().post("/certificates/" + id + "/reinstate", Map.of("reason", "")).status()).isEqualTo(400);
        author.session().post("/certificates/" + id + "/reinstate", Map.of("reason", "Correction reversed")).expect(200);
        runJobs();
        assertThat(only(learner.session()).path("status").asString()).isEqualTo("ISSUED");
        assertThat(only(learner.session()).path("id").asString()).isEqualTo(id);
        assertThat(pdfText(learner.session(), id)).contains("User cert.learner");
        assertThat(jdbc.sql("select count(*) from audit_events where action in ('certificate.revoked', 'certificate.reinstated')").query(Long.class).single())
                .isEqualTo(2);
    }

    @Test
    void verificationIsDisabledByDefaultAndMinimalWhenEnabled() {
        complete(learner);
        runJobs();
        assertThat(jdbc.sql("select verification_hash from certificates").query(String.class).optional()).isEmpty();
        assertThat(client().get("/public/certificates/anything").status()).isEqualTo(404);

        long revision = owner.session().get("/workspace").json().path("revision").asLong();
        owner.session().put("/workspace", Map.of("name", "Test School", "timezone", "Asia/Dhaka", "certificateVerification", true,
                "verificationShowsStudent", false, "expectedRevision", revision)).expect(200);
        TestUser second = registerStudent("second.learner");
        complete(second);
        runJobs();
        String token = jdbc.sql("select verification_token from certificates where verification_hash is not null").query(String.class).single();
        assertThat(token).hasSizeGreaterThanOrEqualTo(32);

        JsonNode verified = client().get("/public/certificates/" + token).expect(200).json();
        assertThat(verified.path("status").asString()).isEqualTo("VALID");
        assertThat(verified.path("workspace").asString()).isEqualTo("Test School");
        assertThat(verified.path("learner").isNull()).isTrue();
        assertThat(verified.toString()).doesNotContain("second.learner@example.test", "second.learner");

        String id = only(second.session()).path("id").asString();
        author.session().post("/certificates/" + id + "/revoke", Map.of("reason", "Fraud")).expect(200);
        JsonNode revoked = client().get("/public/certificates/" + token).expect(200).json();
        assertThat(revoked.path("status").asString()).isEqualTo("REVOKED");
        assertThat(revoked.path("course").isNull()).isTrue();
    }

    @Test
    void namesTheFontCannotDrawFailExplicitlyAndCanBeRetried() {
        String address = "bengali@example.test";
        ApiClient anonymous = client();
        anonymous.post("/auth/register", Map.of("username", "bengali", "displayName", "রহিম উদ্দিন", "email", address, "password", PASSWORD,
                "role", "STUDENT")).expect(201);
        anonymous.post("/auth/email/verify", Map.of("token", tokenFor(address))).expect(204);
        ApiClient session = login("bengali", PASSWORD);
        complete(new TestUser(UUID.fromString(session.get("/auth/me").json().path("id").asString()), "bengali", address, session));
        runJobs();

        JsonNode failed = only(session);
        assertThat(failed.path("status").asString()).isEqualTo("RENDER_FAILED");
        assertThat(failed.path("renderError").asString()).contains("learner name", "cannot display");
        author.session().post("/certificates/" + failed.path("id").asString() + "/retry", null).expect(200);
        runJobs();
        assertThat(only(session).path("status").asString()).isEqualTo("RENDER_FAILED");
    }

    private record Completed(UUID courseId, UUID enrollmentId, String courseTitle) {
    }

    /** Enrolls the learner in a course without requirements and reconciles, which achieves completion at once. */
    private Completed complete(TestUser user) {
        String title = "Certified course " + UUID.randomUUID().toString().substring(0, 8);
        JsonNode course = publishedCourse(author.session(), title, List.of(lesson("Optional", "", false)));
        UUID courseId = UUID.fromString(course.path("courseId").asString());
        UUID enrollmentId = EnrollmentFixtures.enroll(author.session(), user.session(), courseId);
        completions.reconcile(enrollmentId);
        return new Completed(courseId, enrollmentId, title);
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
