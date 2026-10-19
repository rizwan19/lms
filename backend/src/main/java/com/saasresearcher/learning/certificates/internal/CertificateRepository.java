package com.saasresearcher.learning.certificates.internal;

import com.saasresearcher.learning.certificates.api.CertificateStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class CertificateRepository {

    private static final String COLUMNS = """
            id, completion_id, student_id, class_id, status, snapshot::text as snapshot, storage_key, rendered, render_error, revoked_reason, issued_at""";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    CertificateRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Inserts unless the completion already has a certificate; returns whether a row was added. */
    boolean insert(CertificateRow certificate, String verificationHash, Instant now) {
        return jdbc.sql("""
                insert into certificates (id, completion_id, student_id, class_id, status, snapshot, verification_hash, storage_key, created_at, updated_at)
                values (:id, :completion, :student, :class, 'PENDING_RENDER', cast(:snapshot as jsonb), :hash, :key, :now, :now)
                on conflict (completion_id) do nothing
                """)
                .param("id", certificate.id()).param("completion", certificate.completionId()).param("student", certificate.studentId())
                .param("class", certificate.classId()).param("snapshot", json.writeValueAsString(certificate.snapshot()))
                .param("hash", verificationHash).param("key", certificate.storageKey()).param("now", Timestamp.from(now))
                .update() == 1;
    }

    Optional<CertificateRow> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from certificates where id = :id").param("id", id).query(this::row).optional();
    }

    Optional<CertificateRow> lock(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from certificates where id = :id for update").param("id", id).query(this::row).optional();
    }

    Optional<CertificateRow> byCompletion(UUID completionId) {
        return jdbc.sql("select " + COLUMNS + " from certificates where completion_id = :completion").param("completion", completionId)
                .query(this::row).optional();
    }

    Optional<CertificateRow> byVerificationHash(String hash) {
        return jdbc.sql("select " + COLUMNS + " from certificates where verification_hash = :hash").param("hash", hash).query(this::row).optional();
    }

    List<CertificateRow> ofStudent(UUID studentId) {
        return jdbc.sql("select " + COLUMNS + " from certificates where student_id = :student order by created_at desc")
                .param("student", studentId).query(this::row).list();
    }

    List<CertificateRow> ofClass(UUID classId) {
        return jdbc.sql("select " + COLUMNS + " from certificates where class_id = :class order by created_at desc")
                .param("class", classId).query(this::row).list();
    }

    void markIssued(UUID id, Instant now) {
        jdbc.sql("""
                update certificates set status = 'ISSUED', rendered = true, render_error = null, issued_at = coalesce(issued_at, :now),
                    updated_at = :now, version = version + 1
                where id = :id
                """).param("id", id).param("now", Timestamp.from(now)).update();
    }

    void markRendered(UUID id, Instant now) {
        jdbc.sql("update certificates set rendered = true, updated_at = :now where id = :id").param("id", id).param("now", Timestamp.from(now)).update();
    }

    void setStatus(UUID id, CertificateStatus status, String renderError, Instant now) {
        jdbc.sql("update certificates set status = :status, render_error = :error, updated_at = :now, version = version + 1 where id = :id")
                .param("id", id).param("status", status.name()).param("error", renderError).param("now", Timestamp.from(now)).update();
    }

    void revoke(UUID id, String reason, UUID revokedBy, Instant now) {
        jdbc.sql("""
                update certificates set status = 'REVOKED', revoked_reason = :reason, revoked_by = :by, revoked_at = :now, updated_at = :now,
                    version = version + 1
                where id = :id
                """).param("id", id).param("reason", reason).param("by", revokedBy).param("now", Timestamp.from(now)).update();
    }

    private CertificateRow row(ResultSet rs, int row) throws SQLException {
        Timestamp issued = rs.getTimestamp("issued_at");
        return new CertificateRow(rs.getObject("id", UUID.class), rs.getObject("completion_id", UUID.class), rs.getObject("student_id", UUID.class),
                rs.getObject("class_id", UUID.class), CertificateStatus.valueOf(rs.getString("status")),
                json.readValue(rs.getString("snapshot"), CertificateSnapshot.class), rs.getString("storage_key"), rs.getBoolean("rendered"),
                rs.getString("render_error"), rs.getString("revoked_reason"), issued == null ? null : issued.toInstant());
    }
}
