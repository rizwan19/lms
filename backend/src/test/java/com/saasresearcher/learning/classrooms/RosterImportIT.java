package com.saasresearcher.learning.classrooms;

import static com.saasresearcher.learning.support.ClassFixtures.activeClass;
import static com.saasresearcher.learning.support.ClassFixtures.enroll;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publishedCourse;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.notifications.infra.JobRunner;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class RosterImportIT extends PostgresIntegrationTest {


    @Autowired
    private JobRunner jobs;

    private ApiClient admin;
    private TestUser teacher;
    private UUID classId;

    @BeforeEach
    void classroom() {
        admin = setupAdministrator();
        teacher = createUser(admin, "import.teacher", "import@example.test", "TEACHER");
        UUID version = UUID.fromString(publishedCourse(teacher.session(), "Maths", List.of(lesson("One", "", true))).path("versionId").asString());
        classId = activeClass(teacher.session(), version, "Maths A");
    }

    @Test
    void utf8WithBomAndBengaliNamesImportsAndDeliversBothWays() {
        String csv = "﻿username,display_name,email\nrahim,রহিম উদ্দিন,rahim@example.test\nkarim,করিম,\n";
        JsonNode preview = preview(csv).expect(200).json();
        assertThat(preview.path("newAccounts").asInt()).isEqualTo(2);
        assertThat(preview.path("invalidRows").asInt()).isZero();
        assertThat(preview.path("rows").get(0).path("line").asInt()).isEqualTo(2);

        ApiResponse result = commit(preview).expect(200);
        assertThat(result.header("Cache-Control")).contains("no-store");
        assertThat(result.json().path("created").asInt()).isEqualTo(2);
        assertThat(result.json().path("invited").asInt()).isEqualTo(1);
        assertThat(result.json().path("credentials").findValuesAsString("username")).containsExactly("karim");
        assertThat(jdbc.sql("select display_name from users where username = 'rahim'").query(String.class).single()).isEqualTo("রহিম উদ্দিন");
        assertThat(roster()).containsExactlyInAnyOrder("rahim", "karim");

        jobs.drain();
        assertThat(email.sent()).singleElement().satisfies(message -> assertThat(message.to()).isEqualTo("rahim@example.test"));
        String temporary = result.json().path("credentials").get(0).path("temporaryPassword").asString();
        assertThat(login("karim", temporary).get("/auth/me").json().path("passwordChangeRequired").asBoolean()).isTrue();
        assertThat(jdbc.sql("select count(*) from audit_events where metadata::text like :secret").param("secret", "%" + temporary + "%")
                .query(Long.class).single()).isZero();
    }

    @Test
    void rowAndSizeLimitsAreEnforced() {
        StringBuilder thousand = new StringBuilder("username,display_name,email\n");
        for (int i = 0; i < 1000; i++) {
            thousand.append("student").append(i).append(",Student ").append(i).append(",\n");
        }
        assertThat(preview(thousand.toString()).json().path("newAccounts").asInt()).isEqualTo(1000);

        ApiResponse tooMany = preview(thousand + "student1000,Student,\n");
        assertThat(tooMany.status()).isEqualTo(400);
        assertThat(tooMany.code()).isEqualTo("TOO_MANY_ROWS");

        ApiResponse tooLarge = teacher.session().postBytes(importPath("preview"),
                ("username,display_name,email\n" + "x".repeat(1024 * 1024)).getBytes(StandardCharsets.UTF_8), "text/csv");
        assertThat(tooLarge.status()).isEqualTo(413);
    }

    @Test
    void malformedHeaderAndEncodingAreRejected() {
        assertThat(preview("user,name,mail\na,b,c\n").code()).isEqualTo("INVALID_HEADER");
        ApiResponse latin1 = teacher.session().postBytes(importPath("preview"),
                "username,display_name,email\nrene,René,\n".getBytes(StandardCharsets.ISO_8859_1), "text/csv");
        assertThat(latin1.code()).isEqualTo("INVALID_ENCODING");
    }

    @Test
    void duplicatesAfterNormalizationAndFormatProblemsAreReportedPerRow() {
        JsonNode preview = preview("username,display_name,email\nRahim,One,a@example.test\nrahim ,Two,\nokay,Three,a@example.test\nbad name,Four,\nfive,,\n")
                .json();
        JsonNode rows = preview.path("rows");
        assertThat(rows.get(0).path("outcome").asString()).isEqualTo("NEW_ACCOUNT");
        assertThat(rows.get(1).path("errors").get(0).asString()).contains("Duplicate username (also on line 2)");
        assertThat(rows.get(2).path("errors").get(0).asString()).contains("Duplicate email");
        assertThat(rows.get(3).path("outcome").asString()).isEqualTo("INVALID");
        assertThat(rows.get(4).path("outcome").asString()).isEqualTo("INVALID");
        assertThat(preview.path("invalidRows").asInt()).isEqualTo(4);
        assertThat(commit(preview).status()).isEqualTo(422);
    }

    @Test
    void existingAccountsAreEnrolledButNeverModified() {
        createUser(admin, "existing", "existing@example.test", "STUDENT");
        createUser(admin, "mismatch", "mismatch@example.test", "STUDENT");
        TestUser enrolled = createUser(admin, "enrolled.already", null, "STUDENT");
        enroll(teacher.session(), classId, enrolled.username());

        JsonNode preview = preview("""
                username,display_name,email
                existing,Renamed Person,
                mismatch,Mismatch,other@example.test
                import.teacher,Teacher,
                enrolled.already,Again,
                """).json();
        List<String> outcomes = preview.path("rows").findValuesAsString("outcome");
        assertThat(outcomes).containsExactly("ENROLL_EXISTING", "INVALID", "INVALID", "INVALID");
        assertThat(preview.path("rows").get(2).path("errors").get(0).asString()).contains("staff account");

        JsonNode valid = preview("username,display_name,email\nexisting,Renamed Person,\n").json();
        commit(valid).expect(200);
        assertThat(jdbc.sql("select display_name from users where username = 'existing'").query(String.class).single()).isEqualTo("User existing");
        assertThat(roster()).contains("existing");
    }

    @Test
    void oneRowInvalidatedBeforeCommitLeavesZeroChanges() {
        JsonNode preview = preview("username,display_name,email\nfirst,First,\nsecond,Second,\n").json();
        assertThat(preview.path("invalidRows").asInt()).isZero();
        createUser(admin, "second", "second@example.test", "TEACHER");

        ApiResponse result = commit(preview);
        assertThat(result.status()).isEqualTo(422);
        assertThat(result.code()).isEqualTo("IMPORT_INVALID");
        assertThat(roster()).isEmpty();
        assertThat(jdbc.sql("select count(*) from users where username = 'first'").query(Long.class).single()).isZero();
    }

    @Test
    void commitRowsMustMatchThePreviewDigest() {
        JsonNode preview = preview("username,display_name,email\nfirst,First,\n").json();
        Map<String, Object> tampered = Map.of("digest", preview.path("digest").asString(),
                "rows", List.of(Map.of("username", "first", "displayName", "First", "email", ""), Map.of("username", "extra", "displayName", "Extra", "email", "")));
        assertThat(teacher.session().post(importPath("commit"), tampered).code()).isEqualTo("IMPORT_CHANGED");
        assertThat(roster()).isEmpty();
    }

    @Test
    void onlyClassTeachersCanImport() {
        TestUser other = createUser(admin, "other.teacher", "o@example.test", "TEACHER");
        ApiResponse response = other.session().postBytes(importPath("preview"), "username,display_name,email\na1b,A,\n".getBytes(StandardCharsets.UTF_8), "text/csv");
        assertThat(response.status()).isEqualTo(404);
    }

    private ApiResponse preview(String csv) {
        return teacher.session().postBytes(importPath("preview"), csv.getBytes(StandardCharsets.UTF_8), "text/csv");
    }

    private ApiResponse commit(JsonNode preview) {
        List<Map<String, Object>> rows = new ArrayList<>();
        preview.path("rows").forEach(row -> rows.add(Map.of("username", row.path("username").asString(),
                "displayName", row.path("displayName").asString(), "email", row.path("email").isNull() ? "" : row.path("email").asString())));
        return teacher.session().post(importPath("commit"), Map.of("digest", preview.path("digest").asString(), "rows", rows));
    }

    private List<String> roster() {
        return teacher.session().get("/classes/" + classId + "/enrollments?size=100").json().path("items").findValuesAsString("username");
    }

    private String importPath(String action) {
        return "/classes/" + classId + "/imports/" + action;
    }
}
