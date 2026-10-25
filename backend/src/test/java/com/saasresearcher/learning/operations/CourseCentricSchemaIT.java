package com.saasresearcher.learning.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saasresearcher.learning.operations.config.SchemaCompatibilityGuard;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the v2 baseline with Flyway directly against empty PostgreSQL databases (no application context, no JPA) and
 * checks the reset boundary: legacy class-based schemas are refused before anything is migrated.
 */
class CourseCentricSchemaIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @BeforeAll
    static void start() {
        POSTGRES.start();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @Test
    void freshDatabaseMigratesToTheCourseCentricBaseline() {
        DriverManagerDataSource dataSource = database("fresh");
        SchemaCompatibilityGuard.check(dataSource, "public");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/coursecentric").load().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);

        List<String> tables = jdbc.sql("select table_name from information_schema.tables where table_schema = 'public'").query(String.class).list();
        assertThat(tables).contains("users", "workspace", "identity_tokens", "teacher_applications", "courses", "course_versions",
                "course_enrollments", "course_access_requests", "course_access_events", "course_invitations", "lesson_progress", "exams",
                "exam_attempts", "grade_revisions", "course_completions", "certificates", "report_exports", "system_state");
        assertThat(tables).doesNotContain("classes", "class_teachers", "enrollments", "user_roles", "course_teachers", "exam_assignments",
                "institution_settings", "invitations", "password_reset_tokens");
        assertThat(jdbc.sql("select count(*) from information_schema.columns where table_schema = 'public' and column_name = 'class_id'")
                .query(Long.class).single()).isZero();

        assertThat(jdbc.sql("select state from workspace where id = 1").query(Integer.class).single()).as("singleton, not set up").isZero();
        assertThat(jdbc.sql("select maintenance from system_state where id = 1").query(Boolean.class).single()).isFalse();
        assertThat(jdbc.sql("select count(*) from users").query(Long.class).single()).as("no seeded account").isZero();

        jdbc.sql("""
                insert into users (id, username, email, display_name, password_hash, role, status, created_at, updated_at)
                values (gen_random_uuid(), 'student', 'student@example.test', 'Student', 'x', 0, 0, now(), now())
                """).update();
        assertThatThrownBy(() -> jdbc.sql("""
                insert into users (id, username, email, display_name, password_hash, role, status, created_at, updated_at)
                values (gen_random_uuid(), 'admin', 'admin@example.test', 'Admin', 'x', 2, 0, now(), now())
                """).update()).as("exactly two roles").hasMessageContaining("violates check constraint");
        assertThatThrownBy(() -> jdbc.sql("""
                insert into users (id, username, email, display_name, password_hash, role, status, teacher_status, created_at, updated_at)
                values (gen_random_uuid(), 'approved', 'approved@example.test', 'Approved', 'x', 1, 0, 1, now(), now())
                """).update()).as("approval needs a verified email").isNotNull();

        assertThat(constraint(jdbc, "course_enrollments", "UNIQUE")).isPositive();
        assertThat(index(jdbc, "exam_attempts_one_active_idx")).isTrue();
        assertThat(index(jdbc, "course_invitations_one_pending_idx")).isTrue();
        assertThat(index(jdbc, "teacher_applications_one_pending")).isTrue();
        assertThat(index(jdbc, "grade_revisions_single_open")).isTrue();
        assertThat(jdbc.sql("""
                select count(*) from information_schema.table_constraints
                where table_schema = 'public' and constraint_type = 'FOREIGN KEY' and table_name in ('exams', 'exam_attempts', 'lesson_progress')
                """).query(Long.class).single()).as("composite course/edition/enrollment foreign keys").isGreaterThanOrEqualTo(7);
    }

    @Test
    void legacySchemaIsRefusedBeforeMigration() {
        DriverManagerDataSource dataSource = database("legacy");
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("create table classes (id uuid primary key)").update();

        assertThatThrownBy(() -> SchemaCompatibilityGuard.check(dataSource, "public"))
                .hasMessageStartingWith(SchemaCompatibilityGuard.LEGACY_SCHEMA_UNSUPPORTED)
                .hasMessageContaining("classes");
        assertThat(jdbc.sql("select count(*) from information_schema.tables where table_schema = 'public'").query(Long.class).single())
                .as("nothing dropped or added").isEqualTo(1);
    }

    private static DriverManagerDataSource database(String name) {
        JdbcClient.create(source(POSTGRES.getDatabaseName())).sql("create database " + name).update();
        return source(name);
    }

    private static DriverManagerDataSource source(String database) {
        return new DriverManagerDataSource("jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database,
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long constraint(JdbcClient jdbc, String table, String type) {
        return jdbc.sql("select count(*) from information_schema.table_constraints where table_name = :table and constraint_type = :type")
                .param("table", table).param("type", type).query(Long.class).single();
    }

    private static boolean index(JdbcClient jdbc, String name) {
        return jdbc.sql("select count(*) > 0 from pg_indexes where indexname = :name").param("name", name).query(Boolean.class).single();
    }
}
