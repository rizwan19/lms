package com.saasresearcher.learning.reporting;

import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static com.saasresearcher.learning.support.CourseFixtures.saveDraft;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.reporting.api.ExportStatus;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.CompletionFixture;
import com.saasresearcher.learning.support.EnrollmentFixtures;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Course reports for the course author only, filterable by edition; only published grades count as scores. */
class CourseReportingIT extends PostgresIntegrationTest {

    private TestUser owner;
    private TestUser author;

    @BeforeEach
    void author() {
        owner = setupOwner();
        author = approvedTeacher("report.author", owner);
    }

    @Test
    void lessonPercentCountsOnlyRequiredLessonsOfEachLearnersEdition() {
        JsonNode course = publishedCourse(author.session(), "Geography", List.of(lesson("A", "", true), lesson("B", "", true), lesson("C", "", false)));
        UUID courseId = UUID.fromString(course.path("courseId").asString());
        List<String> lessons = course.path("sections").get(0).path("lessons").findValuesAsString("id");
        TestUser learner = registerStudent("geo.learner");
        UUID enrollmentId = EnrollmentFixtures.enroll(author.session(), learner.session(), courseId);
        for (String lesson : List.of(lessons.get(0), lessons.get(2))) {
            learner.session().put("/enrollments/" + enrollmentId + "/lessons/" + lesson + "/progress",
                    Map.of("expectedRevision", 0, "completed", true, "resumeSeconds", 0)).expect(200);
        }

        author.session().post("/courses/" + courseId + "/draft", Map.of("fromVersionId", course.path("versionId").asString())).expect(201);
        saveDraft(author.session(), courseId, "Geography", List.of(section("S", List.of(lesson("Only", "", true)))));
        String secondEdition = publish(author.session(), courseId).path("versionId").asString();
        TestUser later = registerStudent("later.learner");
        EnrollmentFixtures.enroll(author.session(), later.session(), courseId);

        JsonNode all = progress(courseId, null);
        assertThat(all).hasSize(2);
        JsonNode first = byUsername(all, "geo.learner");
        assertThat(first.path("requiredLessons").asInt()).isEqualTo(2);
        assertThat(first.path("completedRequiredLessons").asInt()).isEqualTo(1);
        assertThat(first.path("lessonPercent").asInt()).isEqualTo(50);
        assertThat(byUsername(all, "later.learner").path("requiredLessons").asInt()).isEqualTo(1);
        assertThat(progress(courseId, course.path("versionId").asString()).findValuesAsString("username")).containsExactly("geo.learner");
        assertThat(progress(courseId, secondEdition).findValuesAsString("username")).containsExactly("later.learner");

        JsonNode optional = publishedCourse(author.session(), "Optional", List.of(lesson("Any", "", false)));
        UUID optionalCourse = UUID.fromString(optional.path("courseId").asString());
        EnrollmentFixtures.enroll(author.session(), learner.session(), optionalCourse);
        assertThat(progress(optionalCourse, null).get(0).path("lessonPercent").isNull()).isTrue();
    }

    @Test
    void neverActiveLearnersBecomeInactiveAfterSevenDaysFromTheirGrant() {
        JsonNode course = publishedCourse(author.session(), "History", List.of(lesson("A", "", true)));
        UUID courseId = UUID.fromString(course.path("courseId").asString());
        String lesson = course.path("sections").get(0).path("lessons").get(0).path("id").asString();
        TestUser idle = registerStudent("idle.learner");
        TestUser busy = registerStudent("busy.learner");
        EnrollmentFixtures.enroll(author.session(), idle.session(), courseId);
        UUID busyEnrollment = EnrollmentFixtures.enroll(author.session(), busy.session(), courseId);

        clock.advance(Duration.ofDays(6));
        assertThat(progress(courseId, null).findValuesAsString("inactive")).containsOnly("false");
        busy.session().put("/enrollments/" + busyEnrollment + "/lessons/" + lesson + "/progress",
                Map.of("expectedRevision", 0, "completed", false, "resumeSeconds", 30)).expect(200);
        clock.advance(Duration.ofDays(2));

        JsonNode rows = progress(courseId, null);
        assertThat(byUsername(rows, "idle.learner").path("inactive").asBoolean()).isTrue();
        assertThat(byUsername(rows, "idle.learner").path("lastActivityAt").isNull()).isTrue();
        assertThat(byUsername(rows, "busy.learner").path("inactive").asBoolean()).isFalse();
    }

    @Test
    void scoresShowOnlyPublishedResultsAndFlagPendingGrading() {
        TestUser learner = registerStudent("score.learner");
        CompletionFixture fixture = CompletionFixture.create(author.session(), learner.session(), 1);
        String attempt = fixture.submitAttempt();

        JsonNode pending = scores(fixture.courseId()).get(0).path("scores").get(0);
        assertThat(pending.path("bestEarned").isNull()).isTrue();
        assertThat(pending.path("pendingGrading").asBoolean()).isTrue();
        assertThat(pending.path("examId").asString()).isEqualTo(fixture.examId().toString());
        assertThat(progress(fixture.courseId(), null).get(0).path("pendingGrading").asInt()).isEqualTo(1);

        fixture.publishEssayGrade(attempt, "3");
        JsonNode published = scores(fixture.courseId()).get(0).path("scores").get(0);
        assertThat(published.path("bestEarned").decimalValue()).isEqualByComparingTo("4");
        assertThat(published.path("passed").asBoolean()).isTrue();
        assertThat(published.path("publishedRevisionId").isNull()).isFalse();
        assertThat(progress(fixture.courseId(), null).get(0).path("passedRequiredExams").asInt()).isEqualTo(1);
    }

    @Test
    void exportsAreFormulaSafeUnicodeAndContainNoSecrets() {
        JsonNode course = publishedCourse(author.session(), "Civics", List.of(lesson("A", "", true)));
        UUID courseId = UUID.fromString(course.path("courseId").asString());
        enrollNamed(courseId, "formula", "=HYPERLINK(\"http://evil\")");
        enrollNamed(courseId, "bangla", "সুমাইয়া");

        String exportId = author.session().post("/reports/courses/" + courseId + "/export", Map.of("type", "PROGRESS")).expect(202).json()
                .path("id").asString();
        runJobs();
        ApiResponse download = author.session().get("/reports/exports/" + exportId + "/download").expect(200);
        String csv = download.text();

        assertThat(download.header("Cache-Control")).contains("no-store");
        assertThat(csv).startsWith("learner_name,username,").contains("last_activity_Asia/Dhaka", "সুমাইয়া", "'=HYPERLINK");
        assertThat(csv).doesNotContainIgnoringCase("password").doesNotContain("@example.test");
        assertThat(author.session().get("/notifications").json().path("items").findValuesAsString("title")).contains("Your course report is ready");

        String scores = author.session().post("/reports/courses/" + courseId + "/export", Map.of("type", "SCORES")).expect(202).json().path("id").asString();
        runJobs();
        assertThat(author.session().get("/reports/exports/" + scores + "/download").expect(200).text()).startsWith("learner_name,username");
        assertThat(author.session().get("/reports/courses/" + courseId + "/exports").json()).hasSize(2);
    }

    @Test
    void exportAccessIsRecheckedWhenGeneratedAndDownloaded() {
        JsonNode course = publishedCourse(author.session(), "Art", List.of(lesson("A", "", true)));
        UUID courseId = UUID.fromString(course.path("courseId").asString());
        TestUser other = approvedTeacher("other", owner);

        String ready = author.session().post("/reports/courses/" + courseId + "/export", Map.of("type", "SCORES")).json().path("id").asString();
        runJobs();
        assertThat(other.session().get("/reports/exports/" + ready + "/download").status()).isEqualTo(404);
        assertThat(owner.session().get("/reports/exports/" + ready + "/download").status()).isEqualTo(404);

        String queued = author.session().post("/reports/courses/" + courseId + "/export", Map.of("type", "PROGRESS")).json().path("id").asString();
        long revision = owner.session().get("/workspace/users/" + author.id()).json().path("revision").asLong();
        owner.session().put("/workspace/users/" + author.id() + "/status", Map.of("status", "SUSPENDED", "expectedRevision", revision)).expect(200);
        runJobs();
        assertThat(ExportStatus.fromCode(jdbc.sql("select status from report_exports where id = :id").param("id", UUID.fromString(queued))
                .query(Short.class).single())).isEqualTo(ExportStatus.FAILED);
    }

    @Test
    void unrelatedTeachersAndLearnersCannotReadReports() {
        JsonNode course = publishedCourse(author.session(), "Music", List.of(lesson("A", "", true)));
        UUID courseId = UUID.fromString(course.path("courseId").asString());
        TestUser other = approvedTeacher("other.teacher", owner);
        TestUser learner = registerStudent("music.learner");
        EnrollmentFixtures.enroll(author.session(), learner.session(), courseId);
        for (ApiClient outsider : List.of(other.session(), owner.session(), learner.session())) {
            assertThat(outsider.get("/reports/courses/" + courseId + "/progress").status()).isEqualTo(404);
            assertThat(outsider.get("/reports/courses/" + courseId + "/scores").status()).isEqualTo(404);
            assertThat(outsider.post("/reports/courses/" + courseId + "/export", Map.of("type", "PROGRESS")).status()).isEqualTo(404);
        }
        assertThat(jdbc.sql("select count(*) from information_schema.columns where column_name = 'class_id'").query(Long.class).single()).isZero();
    }

    private void enrollNamed(UUID courseId, String username, String displayName) {
        String address = username + "@example.test";
        ApiClient anonymous = client();
        anonymous.post("/auth/register", Map.of("username", username, "displayName", displayName, "email", address, "password", PASSWORD,
                "role", "STUDENT")).expect(201);
        anonymous.post("/auth/email/verify", Map.of("token", tokenFor(address))).expect(204);
        EnrollmentFixtures.enroll(author.session(), login(username, PASSWORD), courseId);
    }

    private JsonNode progress(UUID courseId, String versionId) {
        return author.session().get("/reports/courses/" + courseId + "/progress" + (versionId == null ? "" : "?versionId=" + versionId)).expect(200)
                .json().path("items");
    }

    private JsonNode scores(UUID courseId) {
        return author.session().get("/reports/courses/" + courseId + "/scores").expect(200).json().path("items");
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
