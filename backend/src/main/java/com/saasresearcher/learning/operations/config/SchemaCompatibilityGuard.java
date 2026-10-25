package com.saasresearcher.learning.operations.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Refuses to migrate a database that still holds the legacy class-based schema. Runs before Flyway and inspects only
 * the configured schema; it never drops, cleans or rewrites anything. Operators create a fresh database instead
 * (docs/runbooks/v2-database-reset.md).
 */
@Configuration(proxyBeanMethods = false)
public class SchemaCompatibilityGuard {

    public static final String LEGACY_SCHEMA_UNSUPPORTED = "LEGACY_SCHEMA_UNSUPPORTED";

    private static final List<String> LEGACY_TABLES = List.of("classes", "class_teachers", "enrollments", "user_roles",
            "course_teachers", "institution_settings", "exam_assignments", "invitations", "password_reset_tokens");

    @Bean
    FlywayMigrationStrategy guardedMigration() {
        return flyway -> {
            check(flyway.getConfiguration().getDataSource(), schemaOf(flyway));
            flyway.migrate();
        };
    }

    /** Throws when the schema contains any table that only the legacy (v1) schema has. */
    public static void check(DataSource dataSource, String schema) {
        try (Connection connection = dataSource.getConnection()) {
            String target = schema != null ? schema : connection.getSchema();
            List<String> found = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "select table_name from information_schema.tables where table_schema = ? and table_name = any (?) order by table_name")) {
                statement.setString(1, target);
                statement.setArray(2, connection.createArrayOf("varchar", LEGACY_TABLES.toArray()));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        found.add(rows.getString(1));
                    }
                }
            }
            if (!found.isEmpty()) {
                throw new IllegalStateException(LEGACY_SCHEMA_UNSUPPORTED + ": schema '" + target + "' contains legacy tables " + found
                        + ". The course-centric release needs a fresh, empty database; see docs/runbooks/v2-database-reset.md.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not inspect the database schema", e);
        }
    }

    private static String schemaOf(Flyway flyway) {
        String[] schemas = flyway.getConfiguration().getSchemas();
        return flyway.getConfiguration().getDefaultSchema() != null ? flyway.getConfiguration().getDefaultSchema()
                : schemas.length > 0 ? schemas[0] : null;
    }
}
