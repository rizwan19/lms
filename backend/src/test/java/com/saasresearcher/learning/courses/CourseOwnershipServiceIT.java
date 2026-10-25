package com.saasresearcher.learning.courses;

import static com.saasresearcher.learning.support.CourseFixtures.createCourse;
import static com.saasresearcher.learning.support.CourseFixtures.draft;
import static com.saasresearcher.learning.support.CourseFixtures.draftRevision;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.saveDraft;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static com.saasresearcher.learning.support.CourseFixtures.uploadPdf;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.courses.api.CourseCatalogService;
import com.saasresearcher.learning.courses.api.PublishedCourse;
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
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** One immutable author per course; workspace approval never opens another author's course. */
class CourseOwnershipServiceIT extends PostgresIntegrationTest {

    @Autowired
    private CourseCatalogService catalog;

    private TestUser owner;
    private TestUser author;
    private TestUser otherTeacher;

    @BeforeEach
    void users() {
        owner = setupOwner();
        author = approvedTeacher("author", owner);
        otherTeacher = approvedTeacher("other.teacher", owner);
    }

    @Test
    void onlyTheAuthorReachesACourse() {
        UUID courseId = createCourse(author.session(), "Algebra");
        TestUser student = registerStudent("student");

        for (ApiClient outsider : List.of(otherTeacher.session(), owner.session())) {
            assertThat(outsider.get("/courses/" + courseId).status()).isEqualTo(404);
            assertThat(outsider.get("/courses/" + courseId + "/preview").status()).isEqualTo(404);
            assertThat(outsider.put("/courses/" + courseId + "/draft", draft(0, "Hijack", List.of())).status()).isEqualTo(404);
            assertThat(outsider.post("/courses/" + courseId + "/archive", null).status()).isEqualTo(404);
            assertThat(outsider.get("/courses").json().path("total").asLong()).isZero();
        }
        assertThat(student.session().get("/courses").status()).isEqualTo(403);
        assertThat(student.session().post("/courses", Map.of("title", "Mine")).status()).isEqualTo(403);
        assertThat(jdbc.sql("select author_id from courses").query(UUID.class).single()).isEqualTo(author.id());
    }

    @Test
    void pendingTeachersDraftPrivatelyButPublishOnlyAfterApproval() {
        TestUser pending = registerTeacher("pending");
        UUID courseId = createCourse(pending.session(), "Waiting");
        saveDraft(pending.session(), courseId, "Waiting", List.of(section("S", List.of(lesson("L", "<p>x</p>", true)))));
        ApiResponse denied = pending.session().post("/courses/" + courseId + "/publish", Map.of("expectedRevision", draftRevision(pending.session(), courseId)));
        assertThat(denied.status()).isEqualTo(403);
        assertThat(denied.code()).isEqualTo("TEACHER_APPROVAL_REQUIRED");

        approve(pending, owner);
        assertThat(publish(pending.session(), courseId).path("edition").asInt()).isEqualTo(1);
    }

    @Test
    void suspendedAuthorsCannotPublish() {
        UUID courseId = createCourse(author.session(), "Suspended");
        saveDraft(author.session(), courseId, "Suspended", List.of(section("S", List.of(lesson("L", "", true)))));
        long revision = owner.session().get("/workspace/users/" + author.id()).json().path("revision").asLong();
        owner.session().put("/workspace/users/" + author.id() + "/status", Map.of("status", "SUSPENDED", "expectedRevision", revision)).expect(200);
        assertThat(author.session().post("/courses/" + courseId + "/publish", Map.of("expectedRevision", 1)).status()).isEqualTo(401);
        assertThat(jdbc.sql("select count(*) from course_versions where status = 1").query(Long.class).single()).isZero();
    }

    @Test
    void lessonTextIsSanitizedAndUnicodeIsKept() {
        UUID courseId = createCourse(author.session(), "Reading");
        String body = "<h2>অধ্যায় ১</h2><p onclick=\"steal()\">Hello <strong>world</strong><script>alert(1)</script></p>"
                + "<iframe src=\"https://evil.example\"></iframe><a href=\"javascript:alert(1)\">bad</a><a href=\"https://ok.example\">ok</a>"
                + "<table><tr><td colspan=\"2\">cell</td></tr></table>";
        JsonNode saved = saveDraft(author.session(), courseId, "Reading", List.of(section("Intro", List.of(lesson("Welcome", body, true)))));

        String stored = saved.path("sections").get(0).path("lessons").get(0).path("bodyHtml").asString();
        assertThat(stored).contains("<h2>অধ্যায় ১</h2>", "<strong>world</strong>", "colspan=\"2\"", "href=\"https://ok.example\"", "rel=\"")
                .doesNotContain("script", "onclick", "iframe", "javascript:");
    }

    @Test
    void videoEmbedsAcceptOnlyProviderIds() {
        UUID courseId = createCourse(author.session(), "Embeds");
        Map<String, Object> youtube = lesson("Video", "", true);
        youtube.put("videoProvider", "YOUTUBE");
        youtube.put("videoRef", "dQw4w9WgXcQ");
        saveDraft(author.session(), courseId, "Embeds", List.of(section("S", List.of(youtube))));

        Map<String, Object> url = lesson("Bad", "", true);
        url.put("videoProvider", "YOUTUBE");
        url.put("videoRef", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        ApiResponse rejected = author.session().put("/courses/" + courseId + "/draft",
                draft(draftRevision(author.session(), courseId), "Embeds", List.of(section("S", List.of(url)))));
        assertThat(rejected.status()).isEqualTo(400);
        assertThat(rejected.json().path("fieldErrors").has("sections[0].lessons[0].videoRef")).isTrue();
    }

    @Test
    void draftsCannotReferenceOtherAuthorsFilesAndPublishChecksFilePurpose() {
        UUID courseId = createCourse(author.session(), "Files");
        String othersPdf = uploadPdf(otherTeacher.session(), "private.pdf");
        Map<String, Object> stolen = lesson("Steal", "", true);
        stolen.put("resourceAssetIds", List.of(othersPdf));
        ApiResponse rejected = author.session().put("/courses/" + courseId + "/draft",
                draft(draftRevision(author.session(), courseId), "Files", List.of(section("S", List.of(stolen)))));
        assertThat(rejected.json().path("fieldErrors").has("sections[0].lessons[0].resourceAssetIds")).isTrue();

        String ownPdf = uploadPdf(author.session(), "notes.pdf");
        Map<String, Object> pdfAsVideo = lesson("Wrong", "", true);
        pdfAsVideo.put("videoAssetId", ownPdf);
        saveDraft(author.session(), courseId, "Files", List.of(section("S", List.of(pdfAsVideo))));
        ApiResponse publish = author.session().post("/courses/" + courseId + "/publish", Map.of("expectedRevision", draftRevision(author.session(), courseId)));
        assertThat(publish.status()).isEqualTo(400);
        assertThat(publish.json().path("fieldErrors").path("sections[0].lessons[0].videoAssetId").asString()).isEqualTo("Choose an uploaded video");
    }

    @Test
    void staleDraftRevisionsAreRejectedAndConcurrentPublishesProduceOneEdition() throws Exception {
        UUID courseId = createCourse(author.session(), "Race");
        long revision = draftRevision(author.session(), courseId);
        author.session().put("/courses/" + courseId + "/draft", draft(revision, "First", List.of(section("S", List.of(lesson("L", "<p>x</p>", true))))))
                .expect(200);
        assertThat(author.session().put("/courses/" + courseId + "/draft", draft(revision, "Second", List.of())).code()).isEqualTo("REVISION_CONFLICT");

        long current = draftRevision(author.session(), courseId);
        ApiClient second = login("author", PASSWORD);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (ApiClient session : List.of(author.session(), second)) {
                results.add(pool.submit(() -> {
                    barrier.await();
                    return session.post("/courses/" + courseId + "/publish", Map.of("expectedRevision", current)).status();
                }));
            }
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.sql("select count(*) from course_versions where course_id = :id and status = 1")
                .param("id", courseId).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void correctionsPublishANewEditionAndLeaveTheOldOneUnchanged() {
        UUID courseId = createCourse(author.session(), "History");
        saveDraft(author.session(), courseId, "History", List.of(section("S", List.of(lesson("Origins", "<p>Teh original</p>", true)))));
        JsonNode first = publish(author.session(), courseId);
        UUID firstVersion = UUID.fromString(first.path("versionId").asString());
        assertThat(first.path("edition").asInt()).isEqualTo(1);
        assertThat(author.session().get("/courses/" + courseId + "/draft").code()).isEqualTo("NO_DRAFT");

        JsonNode copy = author.session().post("/courses/" + courseId + "/draft", Map.of("fromVersionId", firstVersion)).expect(201).json();
        assertThat(author.session().post("/courses/" + courseId + "/draft", Map.of("fromVersionId", firstVersion)).code()).isEqualTo("DRAFT_EXISTS");
        Map<String, Object> corrected = lesson("Origins", "<p>The original</p>", true);
        corrected.put("id", copy.path("sections").get(0).path("lessons").get(0).path("id").asString());
        saveDraft(author.session(), courseId, "History", List.of(section("S", List.of(corrected))));
        JsonNode second = publish(author.session(), courseId);
        assertThat(second.path("edition").asInt()).isEqualTo(2);

        PublishedCourse original = catalog.requirePublished(firstVersion);
        assertThat(original.lessons().findFirst().orElseThrow().bodyHtml()).isEqualTo("<p>Teh original</p>");
        assertThat(original.authorId()).isEqualTo(author.id());
        assertThat(second.path("sections").get(0).path("lessons").get(0).path("bodyHtml").asString()).isEqualTo("<p>The original</p>");
        assertThat(author.session().get("/courses/" + courseId).json().path("editions").size()).isEqualTo(2);
    }

    @Test
    void archivedCoursesLeaveTheActiveListButKeepTheirEditions() {
        UUID courseId = createCourse(author.session(), "Old course");
        saveDraft(author.session(), courseId, "Old course", List.of(section("S", List.of(lesson("L", "", false)))));
        UUID version = UUID.fromString(publish(author.session(), courseId).path("versionId").asString());

        author.session().post("/courses/" + courseId + "/archive", null).expect(204);
        assertThat(author.session().get("/courses").json().path("total").asLong()).isZero();
        assertThat(author.session().get("/courses?status=ARCHIVED").json().path("total").asLong()).isEqualTo(1);
        assertThat(catalog.requirePublished(version).archived()).isTrue();
    }
}
