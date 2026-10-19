package com.saasresearcher.learning.completion;

import static com.saasresearcher.learning.support.ClassFixtures.activeClass;
import static com.saasresearcher.learning.support.ClassFixtures.enroll;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.completion.api.CompletionService;
import com.saasresearcher.learning.notifications.internal.JobRunner;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.CompletionFixture;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class CompletionIT extends PostgresIntegrationTest {

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
        teacher = createUser(admin, "completion.teacher", "completion@example.test", "TEACHER");
        student = createUser(admin, "completion.student", null, "STUDENT");
    }

    @Test
    void requiredLessonsAndPublishedExamPassAreBothNeeded() {
        CompletionFixture fixture = CompletionFixture.create(teacher.session(), student.session(), student.username(), 1);
        fixture.completeLesson(0, true);
        fixture.completeLesson(1, true);
        jobs.drain();
        JsonNode state = fixture.completion();
        assertThat(state.path("status").asString()).isEqualTo("NOT_ELIGIBLE");
        assertThat(state.path("lessons").path("completed").asInt()).isEqualTo(2);

        String attempt = fixture.submitAttempt();
        jobs.drain();
        JsonNode pending = fixture.completion();
        assertThat(pending.path("status").asString()).as("pending grades never satisfy a requirement").isEqualTo("NOT_ELIGIBLE");
        assertThat(pending.path("exams").get(0).path("pendingGrading").asBoolean()).isTrue();

        fixture.publishEssayGrade(attempt, "3");
        assertThat(completionRows()).as("publication only queues work").isZero();
        jobs.drain();
        assertThat(fixture.completion().path("status").asString()).isEqualTo("ACHIEVED");
        assertThat(completionRows()).isEqualTo(1);

        assertThat(fixture.completeLessonResponse(0, false, 1).code()).isEqualTo("COMPLETION_LOCKED");
    }

    @Test
    void optionalLessonsDoNotCountAndZeroRequirementsComplete() {
        JsonNode course = publishedCourse(teacher.session(), "Optional only", List.of(lesson("Any", "", false)));
        UUID classId = activeClass(teacher.session(), UUID.fromString(course.path("versionId").asString()), "Optional");
        UUID enrollmentId = enroll(teacher.session(), classId, student.username());

        assertThat(completions.reconcile(enrollmentId).status().name()).isEqualTo("ACHIEVED");
    }

    @Test
    void duplicateReconciliationsKeepOneCompletion() {
        CompletionFixture fixture = CompletionFixture.create(teacher.session(), student.session(), student.username(), 1);
        fixture.completeLesson(0, true);
        fixture.completeLesson(1, true);
        fixture.publishEssayGrade(fixture.submitAttempt(), "4");
        completions.reconcile(fixture.enrollmentId());
        completions.reconcile(fixture.enrollmentId());
        jobs.drain();
        assertThat(completionRows()).isEqualTo(1);
    }

    @Test
    void theHighestPublishedAttemptCounts() {
        CompletionFixture fixture = CompletionFixture.create(teacher.session(), student.session(), student.username(), 2);
        fixture.completeLesson(0, true);
        fixture.completeLesson(1, true);
        fixture.publishEssayGrade(fixture.submitAttempt(), "0");
        jobs.drain();
        assertThat(fixture.completion().path("status").asString()).isEqualTo("NOT_ELIGIBLE");

        fixture.publishEssayGrade(fixture.submitAttempt(), "2");
        jobs.drain();
        assertThat(fixture.completion().path("status").asString()).isEqualTo("ACHIEVED");
    }

    @Test
    void gradeCorrectionInvalidatesAndRestoresTheSameCompletion() {
        CompletionFixture fixture = CompletionFixture.create(teacher.session(), student.session(), student.username(), 1);
        fixture.completeLesson(0, true);
        fixture.completeLesson(1, true);
        String attempt = fixture.submitAttempt();
        fixture.publishEssayGrade(attempt, "3");
        jobs.drain();
        String completionId = fixture.completion().path("completionId").asString();

        fixture.publishEssayGrade(attempt, "0");
        jobs.drain();
        assertThat(fixture.completion().path("status").asString()).isEqualTo("INVALIDATED");

        fixture.publishEssayGrade(attempt, "4");
        jobs.drain();
        JsonNode restored = fixture.completion();
        assertThat(restored.path("status").asString()).isEqualTo("ACHIEVED");
        assertThat(restored.path("completionId").asString()).isEqualTo(completionId);
        assertThat(completionRows()).isEqualTo(1);
    }

    @Test
    void studentsSeeOnlyTheirOwnRequirements() {
        CompletionFixture fixture = CompletionFixture.create(teacher.session(), student.session(), student.username(), 1);
        TestUser other = createUser(admin, "other.student", null, "STUDENT");
        assertThat(other.session().get("/me/enrollments/" + fixture.enrollmentId() + "/completion").status()).isEqualTo(404);
    }

    private long completionRows() {
        return jdbc.sql("select count(*) from course_completions").query(Long.class).single();
    }
}
