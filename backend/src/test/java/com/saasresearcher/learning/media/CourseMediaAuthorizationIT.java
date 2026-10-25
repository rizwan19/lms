package com.saasresearcher.learning.media;

import static com.saasresearcher.learning.support.CourseFixtures.createCourse;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.saveDraft;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static com.saasresearcher.learning.support.CourseFixtures.uploadPdf;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.support.EnrollmentFixtures;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Course files are readable by their uploader, the course author and active learners of an edition using them; nobody else. */
class CourseMediaAuthorizationIT extends PostgresIntegrationTest {

    private TestUser owner;
    private TestUser author;
    private UUID courseId;
    private String pdf;

    @BeforeEach
    void course() {
        owner = setupOwner();
        author = approvedTeacher("author", owner);
        courseId = createCourse(author.session(), "Files");
        pdf = uploadPdf(author.session(), "worksheet.pdf");
        Map<String, Object> withFile = lesson("Worksheet", "", true);
        withFile.put("resourceAssetIds", List.of(pdf));
        saveDraft(author.session(), courseId, "Files", List.of(section("S", List.of(withFile))));
    }

    @Test
    void workspaceTeachersAndUnrelatedLearnersCannotReadCourseFiles() {
        publish(author.session(), courseId);
        TestUser otherTeacher = approvedTeacher("other", owner);
        TestUser stranger = registerStudent("stranger");
        assertThat(author.session().get("/media/" + pdf + "/content").status()).isEqualTo(200);
        assertThat(owner.session().get("/media/" + pdf + "/content").status()).as("no workspace bypass").isEqualTo(404);
        assertThat(otherTeacher.session().get("/media/" + pdf + "/content").status()).isEqualTo(404);
        assertThat(stranger.session().get("/media/" + pdf + "/content").status()).isEqualTo(404);
        assertThat(client().get("/media/" + pdf + "/content").status()).isEqualTo(401);
    }

    @Test
    void activeLearnersReadFilesUntilSuspendedOrWithdrawn() {
        publish(author.session(), courseId);
        TestUser learner = registerStudent("learner");
        assertThat(learner.session().get("/media/" + pdf + "/content").status()).as("requesting is not access").isEqualTo(404);
        UUID enrollmentId = EnrollmentFixtures.enroll(author.session(), learner.session(), courseId);
        assertThat(learner.session().get("/media/" + pdf + "/content").status()).isEqualTo(200);

        long revision = author.session().get("/courses/" + courseId + "/enrollments").json().path("items").get(0).path("revision").asLong();
        author.session().put("/enrollments/" + enrollmentId + "/status", Map.of("status", "SUSPENDED", "expectedRevision", revision)).expect(200);
        assertThat(learner.session().get("/media/" + pdf + "/content").status()).isEqualTo(404);

        revision = author.session().get("/courses/" + courseId + "/enrollments").json().path("items").get(0).path("revision").asLong();
        author.session().put("/enrollments/" + enrollmentId + "/status", Map.of("status", "ACTIVE", "expectedRevision", revision)).expect(200);
        learner.session().post("/me/enrollments/" + enrollmentId + "/withdraw", null).expect(204);
        assertThat(learner.session().get("/media/" + pdf + "/content").status()).isEqualTo(404);
    }

    @Test
    void draftOnlyFilesStayWithTheAuthor() {
        TestUser learner = registerStudent("early");
        assertThat(learner.session().get("/media/" + pdf + "/content").status()).isEqualTo(404);
        assertThat(author.session().get("/media/" + pdf).status()).isEqualTo(200);
    }

    @Test
    void pendingTeachersUploadCourseFilesButNotBranding() {
        TestUser pending = registerTeacher("pending");
        assertThat(pending.session().postBytes("/media?purpose=DOCUMENT&filename=a.pdf", com.saasresearcher.learning.support.CourseFixtures.PDF,
                "application/pdf").status()).isEqualTo(201);
        assertThat(pending.session().postBytes("/media?purpose=BRANDING_IMAGE&filename=a.png", new byte[] {1}, "image/png").code())
                .isEqualTo("TEACHER_APPROVAL_REQUIRED");
        TestUser learner = registerStudent("uploader");
        assertThat(learner.session().postBytes("/media?purpose=DOCUMENT&filename=a.pdf", com.saasresearcher.learning.support.CourseFixtures.PDF,
                "application/pdf").status()).isEqualTo(403);
    }
}
