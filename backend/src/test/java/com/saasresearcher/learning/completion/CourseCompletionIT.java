package com.saasresearcher.learning.completion;

import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.completion.api.CompletionService;
import com.saasresearcher.learning.support.CompletionFixture;
import com.saasresearcher.learning.support.EnrollmentFixtures;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Completion of the pinned edition: every required lesson plus a published pass of every required course exam. */
class CourseCompletionIT extends PostgresIntegrationTest {

    @Autowired
    private CompletionService completions;

    private TestUser owner;
    private TestUser author;
    private TestUser learner;

    @BeforeEach
    void users() {
        owner = setupOwner();
        author = approvedTeacher("completion.author", owner);
        learner = registerStudent("completion.learner");
    }

    @Test
    void requiredLessonsAndAPublishedExamPassAreBothNeeded() {
        CompletionFixture fixture = CompletionFixture.create(author.session(), learner.session(), 1);
        fixture.completeLesson(0, true);
        fixture.completeLesson(1, true);
        runJobs();
        JsonNode state = fixture.completion();
        assertThat(state.path("status").asString()).isEqualTo("NOT_ELIGIBLE");
        assertThat(state.path("lessons").path("completed").asInt()).isEqualTo(2);
        assertThat(state.toString()).doesNotContain("classId");

        String attempt = fixture.submitAttempt();
        runJobs();
        JsonNode pending = fixture.completion();
        assertThat(pending.path("status").asString()).as("pending grades never satisfy a requirement").isEqualTo("NOT_ELIGIBLE");
        assertThat(pending.path("exams").get(0).path("pendingGrading").asBoolean()).isTrue();

        fixture.publishEssayGrade(attempt, "3");
        assertThat(completionRows()).as("publication only queues work").isZero();
        runJobs();
        assertThat(fixture.completion().path("status").asString()).isEqualTo("ACHIEVED");
        assertThat(completionRows()).isEqualTo(1);
        assertThat(jdbc.sql("select course_id from course_completions").query(UUID.class).single()).isEqualTo(fixture.courseId());

        assertThat(fixture.completeLessonResponse(0, false, 1).code()).isEqualTo("COMPLETION_LOCKED");
    }

    @Test
    void optionalLessonsDoNotCountAndZeroRequirementsComplete() {
        JsonNode course = publishedCourse(author.session(), "Optional only", List.of(lesson("Any", "", false)));
        UUID enrollmentId = EnrollmentFixtures.enroll(author.session(), learner.session(), UUID.fromString(course.path("courseId").asString()));
        assertThat(completions.reconcile(enrollmentId).status().name()).isEqualTo("ACHIEVED");
    }

    @Test
    void duplicateReconciliationsKeepOneCompletion() {
        CompletionFixture fixture = CompletionFixture.create(author.session(), learner.session(), 1);
        fixture.completeLesson(0, true);
        fixture.completeLesson(1, true);
        fixture.publishEssayGrade(fixture.submitAttempt(), "4");
        completions.reconcile(fixture.enrollmentId());
        completions.reconcile(fixture.enrollmentId());
        runJobs();
        assertThat(completionRows()).isEqualTo(1);
    }

    @Test
    void anyPublishedPassCounts() {
        CompletionFixture fixture = CompletionFixture.create(author.session(), learner.session(), 2);
        fixture.completeLesson(0, true);
        fixture.completeLesson(1, true);
        fixture.publishEssayGrade(fixture.submitAttempt(), "0");
        runJobs();
        assertThat(fixture.completion().path("status").asString()).isEqualTo("NOT_ELIGIBLE");

        fixture.publishEssayGrade(fixture.submitAttempt(), "2");
        runJobs();
        assertThat(fixture.completion().path("status").asString()).isEqualTo("ACHIEVED");
    }

    @Test
    void gradeCorrectionInvalidatesAndRestoresTheSameCompletion() {
        CompletionFixture fixture = CompletionFixture.create(author.session(), learner.session(), 1);
        fixture.completeLesson(0, true);
        fixture.completeLesson(1, true);
        String attempt = fixture.submitAttempt();
        fixture.publishEssayGrade(attempt, "3");
        runJobs();
        String completionId = fixture.completion().path("completionId").asString();

        fixture.publishEssayGrade(attempt, "0");
        runJobs();
        assertThat(fixture.completion().path("status").asString()).isEqualTo("INVALIDATED");

        fixture.publishEssayGrade(attempt, "4");
        runJobs();
        JsonNode restored = fixture.completion();
        assertThat(restored.path("status").asString()).isEqualTo("ACHIEVED");
        assertThat(restored.path("completionId").asString()).isEqualTo(completionId);
        assertThat(completionRows()).isEqualTo(1);
    }

    @Test
    void teachersCompleteCoursesTheyLearnAndLearnersSeeOnlyTheirOwnRequirements() {
        TestUser teacher = approvedTeacher("teacher.learner", owner);
        JsonNode course = publishedCourse(author.session(), "Teacher course", List.of(lesson("Only", "", true)));
        UUID enrollmentId = EnrollmentFixtures.enroll(author.session(), teacher.session(), UUID.fromString(course.path("courseId").asString()));
        assertThat(completions.reconcile(enrollmentId).status().name()).isEqualTo("NOT_ELIGIBLE");
        assertThat(learner.session().get("/me/enrollments/" + enrollmentId + "/completion").status()).isEqualTo(404);
        assertThat(author.session().get("/me/enrollments/" + enrollmentId + "/completion").status()).isEqualTo(404);
    }

    private long completionRows() {
        return jdbc.sql("select count(*) from course_completions").query(Long.class).single();
    }
}
