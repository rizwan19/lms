package com.saasresearcher.learning.reporting.internal;

import com.saasresearcher.learning.reporting.api.ReportType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ExportRepository {

    record ExportRow(UUID id, UUID classId, UUID requestedBy, ReportType type, String status, String storageKey, Instant createdAt) {
    }

    private static final String COLUMNS = "id, class_id, requested_by, type, status, storage_key, created_at";

    private final JdbcClient jdbc;

    ExportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(UUID id, UUID classId, UUID requestedBy, ReportType type, Instant now) {
        jdbc.sql("""
                insert into report_exports (id, class_id, requested_by, type, status, storage_key, created_at)
                values (:id, :class, :by, :type, 'PENDING', :key, :now)
                """)
                .param("id", id).param("class", classId).param("by", requestedBy).param("type", type.name())
                .param("key", "reports/" + id).param("now", Timestamp.from(now))
                .update();
    }

    Optional<ExportRow> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from report_exports where id = :id").param("id", id).query(this::row).optional();
    }

    List<ExportRow> ofRequester(UUID requestedBy, UUID classId) {
        return jdbc.sql("select " + COLUMNS + " from report_exports where requested_by = :by and class_id = :class order by created_at desc limit 20")
                .param("by", requestedBy).param("class", classId).query(this::row).list();
    }

    void complete(UUID id, String status, Instant now) {
        jdbc.sql("update report_exports set status = :status, completed_at = :now where id = :id")
                .param("status", status).param("now", Timestamp.from(now)).param("id", id).update();
    }

    private ExportRow row(ResultSet rs, int row) throws SQLException {
        return new ExportRow(rs.getObject("id", UUID.class), rs.getObject("class_id", UUID.class), rs.getObject("requested_by", UUID.class),
                ReportType.valueOf(rs.getString("type")), rs.getString("status"), rs.getString("storage_key"), rs.getTimestamp("created_at").toInstant());
    }
}
