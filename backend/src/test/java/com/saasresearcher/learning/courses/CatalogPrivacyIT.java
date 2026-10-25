package com.saasresearcher.learning.courses;

import static com.saasresearcher.learning.support.CourseFixtures.createCourse;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.saveDraft;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static com.saasresearcher.learning.support.CourseFixtures.uploadPdf;
import static com.saasresearcher.learning.support.ExamFixtures.draftExam;
import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.EnrollmentFixtures;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The public catalog shows summaries of active published courses only, from the latest published edition. */
class CatalogPrivacyIT extends PostgresIntegrationTest {

    private TestUser author;

    @BeforeEach
    void author() {
        TestUser owner = setupOwner();
        author = approvedTeacher("author", owner);
    }

    @Test
    void catalogExposesSummariesButNoProtectedContent() {
        UUID courseId = createCourse(author.session(), "Biology");
        String pdf = uploadPdf(author.session(), "secret.pdf");
        Map<String, Object> video = lesson("Cells", "<p>SECRET LESSON BODY</p>", true);
        video.put("videoProvider", "YOUTUBE");
        video.put("videoRef", "dQw4w9WgXcQ");
        video.put("resourceAssetIds", List.of(pdf));
        author.session().put("/courses/" + courseId + "/draft", Map.of("expectedRevision", 
                author.session().get("/courses/" + courseId + "/draft").json().path("revision").asLong(), "title", "Biology",
                "description", "Living things", "sections", List.of(section("Unit", List.of(video))))).expect(200);
        draftExam(author.session(), courseId, "Secret quiz", "50", List.of(singleChoice("SECRET PROMPT", "1", 0)), true, Map.of());
        publish(author.session(), courseId);
        TestUser learner = registerStudent("learner");
        EnrollmentFixtures.enroll(author.session(), learner.session(), courseId);

        JsonNode list = client().get("/catalog/courses").expect(200).json();
        assertThat(list.path("items").get(0).path("title").asString()).isEqualTo("Biology");
        assertThat(list.path("items").get(0).path("authorName").asString()).isEqualTo("User author");
        assertThat(list.path("items").get(0).path("edition").path("lessonCount").asInt()).isEqualTo(1);
        String detail = client().get("/catalog/courses/" + courseId).expect(200).text();
        assertThat(detail).contains("Cells", "Living things");
        for (String body : List.of(list.toString(), detail)) {
            assertThat(body).doesNotContain("SECRET", "dQw4w9WgXcQ", pdf, "learner", "bodyHtml", "videoRef", "resourceAssetIds", "Secret quiz",
                    "correct", author.id().toString());
        }
    }

    @Test
    void draftsArchivedAndUnpublishedCoursesStayHidden() {
        UUID unpublished = createCourse(author.session(), "Never published");
        UUID published = createCourse(author.session(), "Published title");
        saveDraft(author.session(), published, "Published title", List.of(section("S", List.of(lesson("L", "", true)))));
        publish(author.session(), published);
        author.session().post("/courses/" + published + "/draft", Map.of("fromVersionId",
                author.session().get("/courses/" + published).json().path("editions").get(0).path("versionId").asString())).expect(201);
        saveDraft(author.session(), published, "Leaked draft title", List.of(section("S", List.of(lesson("Draft lesson", "", true)))));
        UUID archived = createCourse(author.session(), "Archived");
        saveDraft(author.session(), archived, "Archived", List.of(section("S", List.of(lesson("L", "", true)))));
        publish(author.session(), archived);
        author.session().post("/courses/" + archived + "/archive", null).expect(204);

        JsonNode list = client().get("/catalog/courses").json();
        assertThat(list.path("items").findValuesAsString("title")).containsExactly("Published title");
        assertThat(client().get("/catalog/courses/" + unpublished).status()).isEqualTo(404);
        assertThat(client().get("/catalog/courses/" + archived).status()).isEqualTo(404);
        assertThat(client().get("/catalog/courses/" + published).text()).doesNotContain("Leaked draft title", "Draft lesson");
        assertThat(client().get("/catalog/courses?search=PUBLISHED").json().path("total").asLong()).isEqualTo(1);
        assertThat(client().get("/catalog/courses?search=zzz").json().path("total").asLong()).isZero();
        assertThat(client().get("/catalog/courses?authorId=" + author.id()).json().path("total").asLong()).isEqualTo(1);
    }

    @Test
    void privateCourseRoutesStayAuthorOnlyDespiteThePublicCatalog() {
        UUID courseId = createCourse(author.session(), "Visible");
        saveDraft(author.session(), courseId, "Visible", List.of(section("S", List.of(lesson("L", "<p>private</p>", true)))));
        publish(author.session(), courseId);
        TestUser learner = registerStudent("curious");
        assertThat(client().get("/catalog/courses/" + courseId).status()).isEqualTo(200);
        assertThat(learner.session().get("/courses/" + courseId).status()).isEqualTo(404);
        assertThat(learner.session().get("/courses/" + courseId + "/preview").status()).isEqualTo(404);
        assertThat(client().get("/courses/" + courseId).status()).isEqualTo(401);
    }
}
