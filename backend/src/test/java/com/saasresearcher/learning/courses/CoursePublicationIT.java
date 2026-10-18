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

import com.saasresearcher.learning.courses.api.CourseCatalog;
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

class CoursePublicationIT extends PostgresIntegrationTest {

    @Autowired
    private CourseCatalog catalog;

    private ApiClient admin;
    private TestUser author;
    private TestUser otherTeacher;

    @BeforeEach
    void users() {
        admin = setupAdministrator();
        author = createUser(admin, "author", "author@example.test", "TEACHER");
        otherTeacher = createUser(admin, "other.teacher", "other@example.test", "TEACHER");
    }

    @Test
    void onlyAssignedAuthorsAndAdministratorsCanReachACourse() {
        UUID courseId = createCourse(author.session(), "Algebra");
        TestUser student = createUser(admin, "student", null, "STUDENT");

        assertThat(otherTeacher.session().get("/courses/" + courseId).status()).isEqualTo(404);
        assertThat(otherTeacher.session().put("/courses/" + courseId + "/draft", draft(0, "Hijack", List.of())).status()).isEqualTo(404);
        assertThat(otherTeacher.session().get("/courses").json().path("total").asLong()).isZero();
        assertThat(student.session().get("/courses").status()).isEqualTo(403);
        assertThat(admin.get("/courses/" + courseId).status()).isEqualTo(200);

        assertThat(author.session().put("/courses/" + courseId + "/teachers", Map.of("teacherIds", List.of(student.id()))).status()).isEqualTo(403);
        assertThat(admin.put("/courses/" + courseId + "/teachers", Map.of("teacherIds", List.of(student.id()))).status()).isEqualTo(400);
        admin.put("/courses/" + courseId + "/teachers", Map.of("teacherIds", List.of(author.id(), otherTeacher.id()))).expect(200);
        assertThat(otherTeacher.session().get("/courses/" + courseId).status()).isEqualTo(200);
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

        Map<String, Object> both = lesson("Both", "", true);
        both.put("videoProvider", "VIMEO");
        both.put("videoRef", "123456789");
        both.put("videoAssetId", UUID.randomUUID().toString());
        assertThat(author.session().put("/courses/" + courseId + "/draft",
                draft(draftRevision(author.session(), courseId), "Embeds", List.of(section("S", List.of(both))))).status()).isEqualTo(400);
    }

    @Test
    void draftsCannotReferenceOtherPeoplesFilesAndPublishChecksFilePurpose() {
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

        Map<String, Object> withResource = lesson("Notes", "", true);
        withResource.put("resourceAssetIds", List.of(ownPdf));
        saveDraft(author.session(), courseId, "Files", List.of(section("S", List.of(withResource))));
        publish(author.session(), courseId);

        admin.put("/courses/" + courseId + "/teachers", Map.of("teacherIds", List.of(author.id(), otherTeacher.id()))).expect(200);
        assertThat(otherTeacher.session().get("/media/" + ownPdf + "/content").status()).isEqualTo(200);
    }

    @Test
    void publishingRequiresAtLeastOneLesson() {
        UUID courseId = createCourse(author.session(), "Empty");
        ApiResponse publish = author.session().post("/courses/" + courseId + "/publish", Map.of("expectedRevision", draftRevision(author.session(), courseId)));
        assertThat(publish.status()).isEqualTo(400);
        assertThat(publish.json().path("fieldErrors").has("sections")).isTrue();
    }

    @Test
    void staleDraftRevisionsAreRejected() {
        UUID courseId = createCourse(author.session(), "Stale");
        long revision = draftRevision(author.session(), courseId);
        author.session().put("/courses/" + courseId + "/draft", draft(revision, "First", List.of())).expect(200);
        ApiResponse stale = author.session().put("/courses/" + courseId + "/draft", draft(revision, "Second", List.of()));
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.code()).isEqualTo("REVISION_CONFLICT");
    }

    @Test
    void concurrentPublishesProduceOneEdition() throws Exception {
        UUID courseId = createCourse(author.session(), "Race");
        saveDraft(author.session(), courseId, "Race", List.of(section("S", List.of(lesson("L", "<p>x</p>", true)))));
        long revision = draftRevision(author.session(), courseId);
        ApiClient second = login("author", PASSWORD);
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (ApiClient session : List.of(author.session(), second)) {
                results.add(pool.submit(() -> {
                    barrier.await();
                    return session.post("/courses/" + courseId + "/publish", Map.of("expectedRevision", revision)).status();
                }));
            }
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.sql("select count(*) from course_versions where course_id = :id and status = 'PUBLISHED'")
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
        assertThat(second.path("sections").get(0).path("lessons").get(0).path("bodyHtml").asString()).isEqualTo("<p>The original</p>");
        assertThat(author.session().get("/courses/" + courseId + "/preview?versionId=" + firstVersion).json()
                .path("sections").get(0).path("lessons").get(0).path("bodyHtml").asString()).isEqualTo("<p>Teh original</p>");
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
        assertThat(catalog.requirePublished(version).requiredLessonIds()).isEmpty();
    }
}
