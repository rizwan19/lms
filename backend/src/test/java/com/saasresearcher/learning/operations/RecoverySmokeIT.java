package com.saasresearcher.learning.operations;

import static com.saasresearcher.learning.support.ClassFixtures.activeClass;
import static com.saasresearcher.learning.support.ClassFixtures.enroll;
import static com.saasresearcher.learning.support.CourseFixtures.PDF;
import static com.saasresearcher.learning.support.CourseFixtures.createCourse;
import static com.saasresearcher.learning.support.CourseFixtures.draft;
import static com.saasresearcher.learning.support.CourseFixtures.draftRevision;
import static com.saasresearcher.learning.support.CourseFixtures.lesson;
import static com.saasresearcher.learning.support.CourseFixtures.publish;
import static com.saasresearcher.learning.support.CourseFixtures.section;
import static com.saasresearcher.learning.support.ExamFixtures.publishedExam;
import static com.saasresearcher.learning.support.ExamFixtures.singleChoice;
import static com.saasresearcher.learning.support.ExamFixtures.written;
import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.LearningPortalApplication;
import com.saasresearcher.learning.notifications.infra.JobRunner;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Backs up a complete fixture with scripts/backup.sh, restores it with scripts/restore.sh into a different, empty
 * PostgreSQL and media directory, boots a second application on the restored data and checks sign-in, ownership,
 * media, grades and certificates. Also checks that the scripts refuse wrong keys, corrupt archives, missing media and
 * live targets, and that maintenance mode pauses mutations and job claims.
 */
class RecoverySmokeIT extends PostgresIntegrationTest {

    private static final Path SCRIPTS = Path.of("../scripts").toAbsolutePath().normalize();
    private static final Path MEDIA = Path.of("build/test-media/assets").toAbsolutePath();
    private static final JsonNode FIXTURE = load();

    @Autowired
    private PostgreSQLContainer source;

    @Autowired
    private JobRunner jobs;

    @TempDir
    private Path work;

    private Path key;

    @BeforeEach
    void key() throws IOException {
        key = work.resolve("backup.key");
        Files.writeString(key, "correct-backup-passphrase-" + UUID.randomUUID());
    }

    @Test
    void fullBackupRestoresIntoACleanInstance() throws Exception {
        Fixture fixture = buildFixture();
        Path archive = backup(Map.of()).archive();
        assertThat(jdbc.sql("select maintenance from system_state").query(Boolean.class).single()).isFalse();

        try (PostgreSQLContainer target = new PostgreSQLContainer("postgres:17-alpine")) {
            target.start();
            Path restoredMedia = work.resolve("restored-media");
            Result restored = run("restore.sh", List.of(archive.toString(), "--target", "isolated"), targetEnv(target, restoredMedia, key));
            assertThat(restored.exitCode()).as(restored.output()).isZero();

            try (ConfigurableApplicationContext app = start(target, restoredMedia)) {
                ApiClient student = new ApiClient("http://localhost:" + app.getEnvironment().getProperty("local.server.port") + "/api/v1");
                student.get("/auth/csrf");
                student.post("/auth/login", Map.of("username", FIXTURE.path("student").asString(), "password", PASSWORD)).expect(200);
                student.get("/auth/csrf");

                assertThat(student.get("/me/enrollments/" + fixture.enrollmentId()).expect(200).json().path("course").path("title").asString())
                        .isEqualTo(FIXTURE.path("course").asString());
                assertThat(student.get("/media/" + fixture.videoId() + "/content").expect(200).bytes()).isEqualTo(fixture.video());
                assertThat(student.get("/media/" + fixture.pdfId() + "/content").expect(200).bytes()).isEqualTo(PDF);
                assertThat(student.get("/me/results").json().get(0).path("earned").decimalValue())
                        .isEqualByComparingTo(FIXTURE.path("expectedEarned").asString());
                JsonNode certificate = student.get("/me/certificates").json().get(0);
                assertThat(certificate.path("status").asString()).isEqualTo(FIXTURE.path("certificate").asString());
                assertThat(new String(student.get("/certificates/" + certificate.path("id").asString() + "/download").expect(200).bytes(), 0, 5))
                        .isEqualTo("%PDF-");

                ApiClient outsider = new ApiClient("http://localhost:" + app.getEnvironment().getProperty("local.server.port") + "/api/v1");
                outsider.get("/auth/csrf");
                outsider.post("/auth/login", Map.of("username", FIXTURE.path("outsider").asString(), "password", PASSWORD)).expect(200);
                assertThat(outsider.get("/media/" + fixture.pdfId() + "/content").status()).isEqualTo(404);
            }
        }
    }

    @Test
    void wrongKeysCorruptArchivesAndLiveTargetsAreRefused() throws Exception {
        setupAdministrator();
        Path archive = backup(Map.of()).archive();
        assertThat(run("verify-backup.sh", List.of(archive.toString()), sourceEnv(key)).exitCode()).isZero();

        Path wrongKey = work.resolve("wrong.key");
        Files.writeString(wrongKey, "not the right passphrase");
        try (PostgreSQLContainer target = new PostgreSQLContainer("postgres:17-alpine")) {
            target.start();
            Result wrong = run("restore.sh", List.of(archive.toString(), "--target", "isolated"), targetEnv(target, work.resolve("m1"), wrongKey));
            assertThat(wrong.exitCode()).isNotZero();
            assertThat(wrong.output()).contains("cannot decrypt");
            assertThat(tableCount(target)).as("target untouched").isZero();
        }

        byte[] bytes = Files.readAllBytes(archive);
        bytes[bytes.length / 2] ^= 0x7F;
        Files.write(archive, bytes);
        Result corrupt = run("verify-backup.sh", List.of(archive.toString()), sourceEnv(key));
        assertThat(corrupt.exitCode()).isNotZero();
        assertThat(corrupt.output()).contains("checksum mismatch");

        Result live = run("restore.sh", List.of(archive.toString(), "--target", "isolated"), targetEnv(source, work.resolve("m2"), key));
        assertThat(live.exitCode()).isNotZero();
        assertThat(live.output()).contains("not empty");

        Map<String, String> noKey = sourceEnv(key);
        noKey.remove("LEARNING_BACKUP_KEY_FILE");
        assertThat(run("backup.sh", List.of("--output", work.resolve("none").toString()), noKey).output()).contains("LEARNING_BACKUP_KEY_FILE");
    }

    @Test
    void missingMediaAbortsTheBackupAndEndsMaintenance() throws Exception {
        ApiClient admin = setupAdministrator();
        TestUser teacher = createUser(admin, "missing.teacher", "missing@example.test", "TEACHER");
        String id = teacher.session().postBytes("/media?purpose=DOCUMENT&filename=gone.pdf", PDF, "application/pdf").expect(201).json().path("id").asString();
        Files.delete(MEDIA.resolve("assets/" + id));

        Result result = backup(Map.of()).result();
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("missing media file: assets/" + id);
        assertThat(jdbc.sql("select maintenance from system_state").query(Boolean.class).single()).isFalse();
    }

    @Test
    void maintenanceModePausesMutationsAndJobClaims() {
        ApiClient admin = setupAdministrator();
        TestUser student = createUser(admin, "paused.student", null, "STUDENT");
        admin.put("/admin/maintenance", Map.of("active", true, "reason", "backup")).expect(200);
        recordingJobs.failNext(0);
        jdbc.sql("insert into background_jobs (id, type, payload, status, attempts, available_at, created_at, updated_at) "
                + "values (gen_random_uuid(), 'test.recording', '{}', 'PENDING', 0, :now, :now, :now)")
                .param("now", java.sql.Timestamp.from(clock.instant())).update();
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(student.session().put("/auth/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "another long passphrase")).code())
                .isEqualTo("MAINTENANCE");
        assertThat(student.session().get("/auth/me").status()).isEqualTo(200);
        assertThat(jobs.runOnce()).isZero();

        admin.put("/admin/maintenance", Map.of("active", false)).expect(200);
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(jobs.runOnce()).isEqualTo(1);
    }

    private record Fixture(UUID enrollmentId, String videoId, String pdfId, byte[] video) {
    }

    private Fixture buildFixture() throws IOException {
        ApiClient admin = setupAdministrator();
        TestUser teacher = createUser(admin, FIXTURE.path("teacher").asString(), "recovery@example.test", "TEACHER");
        TestUser student = createUser(admin, FIXTURE.path("student").asString(), null, "STUDENT");
        createUser(admin, FIXTURE.path("outsider").asString(), null, "STUDENT");
        byte[] video;
        try (InputStream in = RecoverySmokeIT.class.getResourceAsStream("/media/h264-aac.mp4")) {
            video = in.readAllBytes();
        }
        String videoId = teacher.session().postBytes("/media?purpose=VIDEO&filename=experiment.mp4", video, "video/mp4").expect(201).json().path("id").asString();
        String pdfId = teacher.session().postBytes("/media?purpose=DOCUMENT&filename=worksheet.pdf", PDF, "application/pdf").expect(201).json().path("id").asString();

        UUID courseId = createCourse(teacher.session(), FIXTURE.path("course").asString());
        JsonNode exam = publishedExam(teacher.session(), courseId, FIXTURE.path("exam").asString(), "50",
                List.of(singleChoice("Pick A", "1", 0), written("Essay", "4")));
        Map<String, Object> videoLesson = lesson(FIXTURE.path("videoLesson").asString(), "<p>Watch</p>", true);
        videoLesson.put("videoAssetId", videoId);
        Map<String, Object> pdfLesson = lesson(FIXTURE.path("documentLesson").asString(), "<p>Read</p>", true);
        pdfLesson.put("resourceAssetIds", List.of(pdfId));
        teacher.session().put("/courses/" + courseId + "/draft", draft(draftRevision(teacher.session(), courseId), FIXTURE.path("course").asString(),
                List.of(section("Unit", List.of(videoLesson, pdfLesson))), List.of(exam.path("versionId").asString()))).expect(200);
        JsonNode edition = publish(teacher.session(), courseId);
        UUID classId = activeClass(teacher.session(), UUID.fromString(edition.path("versionId").asString()), "Recovery class");
        UUID enrollmentId = enroll(teacher.session(), classId, student.username());

        for (JsonNode lesson : edition.path("sections").get(0).path("lessons")) {
            student.session().put("/enrollments/" + enrollmentId + "/lessons/" + lesson.path("id").asString() + "/progress",
                    Map.of("expectedRevision", 0, "completed", true, "resumeSeconds", 0)).expect(200);
        }
        String assignment = teacher.session().get("/exam-assignments?classId=" + classId).json().get(0).path("id").asString();
        String attempt = student.session().post("/exam-assignments/" + assignment + "/attempts", null).expect(200).json().path("id").asString();
        JsonNode single = question(exam, "Pick A");
        student.session().put("/attempts/" + attempt + "/answers/" + single.path("id").asString(),
                Map.of("expectedRevision", 0, "selectedOptionIds", List.of(single.path("options").get(0).path("id").asString()))).expect(200);
        student.session().post("/attempts/" + attempt + "/submit", null).expect(200);
        String grading = "/grading/attempts/" + attempt;
        teacher.session().put(grading + "/answers/" + question(exam, "Essay").path("id").asString(), Map.of("expectedRevision",
                teacher.session().get(grading).json().path("revision").path("version").asLong(), "points", FIXTURE.path("essayPoints").asString(),
                "feedback", "Good")).expect(200);
        teacher.session().post(grading + "/finalize", Map.of("expectedRevision", teacher.session().get(grading).json().path("revision").path("version").asLong())).expect(200);
        teacher.session().post(grading + "/publish", Map.of("expectedRevision", teacher.session().get(grading).json().path("revision").path("version").asLong())).expect(200);
        jobs.drain();
        assertThat(student.session().get("/me/certificates").json().get(0).path("status").asString()).isEqualTo("ISSUED");
        return new Fixture(enrollmentId, videoId, pdfId, video);
    }

    private record Backup(Path archive, Result result) {
    }

    private Backup backup(Map<String, String> overrides) throws Exception {
        Path output = work.resolve("backups");
        Map<String, String> env = sourceEnv(key);
        env.putAll(overrides);
        Result result = run("backup.sh", List.of("--output", output.toString()), env);
        String last = result.output().strip().lines().reduce((first, second) -> second).orElse("");
        return new Backup(result.exitCode() == 0 ? Path.of(last) : null, result);
    }

    private Map<String, String> sourceEnv(Path keyFile) {
        Map<String, String> env = new HashMap<>();
        env.put("LEARNING_BACKUP_KEY_FILE", keyFile.toString());
        env.put("MEDIA_DIR", MEDIA.toString());
        env.put("PSQL", docker(source, "psql"));
        env.put("PG_DUMP", docker(source, "pg_dump"));
        env.put("PG_RESTORE", "docker exec -i " + source.getContainerId() + " pg_restore");
        return env;
    }

    private static Map<String, String> targetEnv(PostgreSQLContainer target, Path media, Path keyFile) {
        Map<String, String> env = new HashMap<>();
        env.put("LEARNING_BACKUP_KEY_FILE", keyFile.toString());
        env.put("RESTORE_MEDIA_DIR", media.toString());
        env.put("PSQL", docker(target, "psql"));
        env.put("PG_RESTORE", docker(target, "pg_restore"));
        return env;
    }

    private static String docker(PostgreSQLContainer container, String tool) {
        return "docker exec -i " + container.getContainerId() + " " + tool + " -U " + container.getUsername() + " -d " + container.getDatabaseName();
    }

    private record Result(int exitCode, String output) {
    }

    private static Result run(String script, List<String> args, Map<String, String> env) throws Exception {
        List<String> command = new java.util.ArrayList<>(List.of("bash", SCRIPTS.resolve(script).toString()));
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().putAll(env);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.waitFor(), output);
    }

    private static ConfigurableApplicationContext start(PostgreSQLContainer database, Path media) throws IOException {
        Path staging = Files.createTempDirectory("restored-staging");
        return new SpringApplicationBuilder(LearningPortalApplication.class).profiles("test").run(
                "--spring.datasource.url=" + database.getJdbcUrl(),
                "--spring.datasource.username=" + database.getUsername(),
                "--spring.datasource.password=" + database.getPassword(),
                "--server.port=0", "--management.server.port=0",
                "--learning.media.storage-root=" + media, "--learning.media.staging-dir=" + staging,
                "--learning.jobs.enabled=false");
    }

    private static long tableCount(PostgreSQLContainer container) throws Exception {
        Process process = new ProcessBuilder("bash", "-c", docker(container, "psql") + " -X -A -t -c \"select count(*) from information_schema.tables where table_schema = 'public'\"")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        process.waitFor();
        return Long.parseLong(output);
    }

    private static JsonNode question(JsonNode exam, String prompt) {
        for (JsonNode question : exam.path("questions")) {
            if (question.path("prompt").asString().equals(prompt)) {
                return question;
            }
        }
        throw new IllegalArgumentException(prompt);
    }

    private static JsonNode load() {
        try (InputStream in = RecoverySmokeIT.class.getResourceAsStream("/fixtures/recovery-manifest.json")) {
            return JsonMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
