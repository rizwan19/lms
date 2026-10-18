package com.saasresearcher.learning.classrooms;

import static com.saasresearcher.learning.support.ClassFixtures.activeClass;
import static com.saasresearcher.learning.support.ClassFixtures.createClass;
import static com.saasresearcher.learning.support.ClassFixtures.enroll;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static com.saasresearcher.learning.support.CourseFixtures.uploadPdf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasresearcher.learning.classrooms.api.EnrollmentAccess;
import com.saasresearcher.learning.classrooms.api.StudentEnrollment;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class EnrollmentAccessIT extends PostgresIntegrationTest {

    @Autowired
    private EnrollmentAccess access;

    private ApiClient admin;
    private TestUser teacher;
    private UUID versionId;
    private UUID courseId;

    @BeforeEach
    void course() {
        admin = setupAdministrator();
        teacher = createUser(admin, "class.teacher", "class@example.test", "TEACHER");
        JsonNode course = publishedCourse(teacher.session(), "Biology", List.of(lesson("Cells", "<p>Cells</p>", true)));
        versionId = UUID.fromString(course.path("versionId").asString());
        courseId = UUID.fromString(course.path("courseId").asString());
    }

    @Test
    void teacherReassignmentTakesEffectOnTheNextRequest() {
        UUID classId = activeClass(teacher.session(), versionId, "Biology A");
        TestUser replacement = createUser(admin, "replacement", "replacement@example.test", "TEACHER");
        assertThat(replacement.session().get("/classes/" + classId).status()).isEqualTo(404);

        admin.put("/classes/" + classId + "/teachers", Map.of("teacherIds", List.of(replacement.id()))).expect(200);

        assertThat(replacement.session().get("/classes/" + classId).status()).isEqualTo(200);
        assertThat(teacher.session().get("/classes/" + classId).status()).isEqualTo(404);
        assertThat(teacher.session().get("/classes/" + classId + "/enrollments").status()).isEqualTo(404);
    }

    @Test
    void courseAuthorshipDoesNotGrantClassAccess() {
        UUID classId = activeClass(teacher.session(), versionId, "Biology A");
        TestUser coAuthor = createUser(admin, "co.author", "co@example.test", "TEACHER");
        admin.put("/courses/" + courseId + "/teachers", Map.of("teacherIds", List.of(teacher.id(), coAuthor.id()))).expect(200);

        assertThat(coAuthor.session().get("/classes/" + classId).status()).isEqualTo(404);
        assertThat(coAuthor.session().get("/classes").json().path("total").asLong()).isZero();

        TestUser outsider = createUser(admin, "outsider", "outsider@example.test", "TEACHER");
        assertThat(outsider.session().post("/classes", Map.of("courseVersionId", versionId, "name", "Mine")).status()).isEqualTo(404);
    }

    @Test
    void accessWindowsAndClassStateGovernLearning() {
        Instant opens = clock.instant().plus(Duration.ofDays(1));
        UUID classId = createClass(teacher.session(), versionId, "Scheduled", opens, opens.plus(Duration.ofDays(30)));
        TestUser student = createUser(admin, "window.student", null, "STUDENT");
        UUID enrollmentId = enroll(teacher.session(), classId, student.username());
        Actor actor = studentActor(student);

        assertInactive(enrollmentId, actor);
        teacher.session().post("/classes/" + classId + "/activate", null).expect(200);
        assertInactive(enrollmentId, actor);

        clock.advance(Duration.ofDays(1));
        assertThat(access.requireActive(enrollmentId, actor).courseVersionId()).isEqualTo(versionId);

        clock.advance(Duration.ofDays(30));
        assertInactive(enrollmentId, actor);
    }

    @Test
    void rosterMembershipIsUniqueAndLimitedToStudents() {
        UUID classId = activeClass(teacher.session(), versionId, "Biology A");
        TestUser student = createUser(admin, "unique.student", null, "STUDENT");
        enroll(teacher.session(), classId, student.username());

        ApiResponse again = teacher.session().post("/classes/" + classId + "/enrollments", Map.of("username", "UNIQUE.student"));
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("ALREADY_ENROLLED");
        assertThat(teacher.session().post("/classes/" + classId + "/enrollments", Map.of("username", "class.teacher")).status()).isEqualTo(400);
        assertThat(teacher.session().post("/classes/" + classId + "/enrollments", Map.of("username", "nobody")).status()).isEqualTo(400);
        assertThat(student.session().get("/notifications").json().path("total").asLong()).isEqualTo(1);
    }

    @Test
    void suspensionAndArchivingStopLearningButKeepHistoryReadable() {
        UUID classId = activeClass(teacher.session(), versionId, "Biology A");
        TestUser student = createUser(admin, "history.student", null, "STUDENT");
        UUID enrollmentId = enroll(teacher.session(), classId, student.username());
        Actor actor = studentActor(student);
        assertThat(access.requireActive(enrollmentId, actor).status().name()).isEqualTo("ACTIVE");

        teacher.session().put("/classes/" + classId + "/enrollments/" + enrollmentId, Map.of("status", "SUSPENDED")).expect(200);
        assertInactive(enrollmentId, actor);
        StudentEnrollment history = access.requireOwn(enrollmentId, actor);
        assertThat(history.accessOpen()).isFalse();
        assertThat(access.enrollmentsOf(student.id())).hasSize(1);

        teacher.session().put("/classes/" + classId + "/enrollments/" + enrollmentId, Map.of("status", "ACTIVE")).expect(200);
        teacher.session().post("/classes/" + classId + "/archive", null).expect(200);
        assertInactive(enrollmentId, actor);
        assertThat(access.requireOwn(enrollmentId, actor).className()).isEqualTo("Biology A");

        TestUser other = createUser(admin, "other.student", null, "STUDENT");
        assertThatThrownBy(() -> access.requireOwn(enrollmentId, studentActor(other)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(404));
    }

    @Test
    void oneEditionServesTwoClassesWithIsolatedRosters() {
        UUID morning = activeClass(teacher.session(), versionId, "Morning");
        TestUser eveningTeacher = createUser(admin, "evening.teacher", "evening@example.test", "TEACHER");
        admin.put("/courses/" + courseId + "/teachers", Map.of("teacherIds", List.of(teacher.id(), eveningTeacher.id()))).expect(200);
        UUID evening = activeClass(eveningTeacher.session(), versionId, "Evening");
        TestUser early = createUser(admin, "early.bird", null, "STUDENT");
        TestUser late = createUser(admin, "night.owl", null, "STUDENT");
        enroll(teacher.session(), morning, early.username());
        enroll(eveningTeacher.session(), evening, late.username());

        assertThat(teacher.session().get("/classes/" + morning + "/enrollments").json().path("items").findValuesAsString("username"))
                .containsExactly("early.bird");
        assertThat(eveningTeacher.session().get("/classes/" + evening + "/enrollments").json().path("items").findValuesAsString("username"))
                .containsExactly("night.owl");
        assertThat(eveningTeacher.session().get("/classes/" + morning + "/enrollments").status()).isEqualTo(404);
    }

    @Test
    void lessonFilesFollowActiveEnrollment() {
        String pdf = uploadPdf(teacher.session(), "worksheet.pdf");
        Map<String, Object> withFile = lesson("Worksheet", "", true);
        withFile.put("resourceAssetIds", List.of(pdf));
        UUID fileVersion = UUID.fromString(publishedCourse(teacher.session(), "Chemistry", List.of(withFile)).path("versionId").asString());
        UUID classId = activeClass(teacher.session(), fileVersion, "Chemistry A");
        TestUser enrolled = createUser(admin, "enrolled", null, "STUDENT");
        TestUser outsider = createUser(admin, "not.enrolled", null, "STUDENT");
        UUID enrollmentId = enroll(teacher.session(), classId, enrolled.username());

        assertThat(enrolled.session().get("/media/" + pdf + "/content").status()).isEqualTo(200);
        assertThat(outsider.session().get("/media/" + pdf + "/content").status()).isEqualTo(404);

        teacher.session().put("/classes/" + classId + "/enrollments/" + enrollmentId, Map.of("status", "WITHDRAWN")).expect(200);
        assertThat(enrolled.session().get("/media/" + pdf + "/content").status()).isEqualTo(404);
    }

    @Test
    void classTeachersCanResetTheirStudentsButNotOthers() {
        UUID classId = activeClass(teacher.session(), versionId, "Biology A");
        TestUser mine = createUser(admin, "my.student", null, "STUDENT");
        TestUser notMine = createUser(admin, "their.student", null, "STUDENT");
        TestUser staff = createUser(admin, "staff.member", "staff@example.test", "TEACHER");
        enroll(teacher.session(), classId, mine.username());

        assertThat(teacher.session().post("/users/" + mine.id() + "/assisted-reset", null).status()).isEqualTo(200);
        assertThat(teacher.session().post("/users/" + notMine.id() + "/assisted-reset", null).status()).isEqualTo(404);
        assertThat(teacher.session().post("/users/" + staff.id() + "/assisted-reset", null).status()).isEqualTo(404);
        assertThat(teacher.session().get("/users").json().path("items").findValuesAsString("username")).containsExactly("my.student");
    }

    private void assertInactive(UUID enrollmentId, Actor actor) {
        assertThatThrownBy(() -> access.requireActive(enrollmentId, actor))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("ENROLLMENT_INACTIVE"));
    }

    private static Actor studentActor(TestUser student) {
        return new Actor(student.id(), Set.of(Role.STUDENT));
    }
}
