package com.saasresearcher.learning.notifications.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.notifications.api.EmailSender;
import com.saasresearcher.learning.notifications.api.JobStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Approved-teacher visibility into delivery health: SMTP configuration and terminally failed jobs. */
@RestController
@RequestMapping("/admin")
public class OperationsController {

    private final JdbcClient jdbc;
    private final EmailSender email;

    OperationsController(JdbcClient jdbc, EmailSender email) {
        this.jdbc = jdbc;
        this.email = email;
    }

    @GetMapping("/system-status")
    SystemStatus status(Actor actor) {
        actor.requireApprovedTeacher();
        long failed = jdbc.sql("select count(*) from background_jobs where status = :failed").param("failed", JobStatus.FAILED.code()).query(Long.class).single();
        return new SystemStatus(email.isConfigured(), failed);
    }

    @GetMapping("/failed-jobs")
    PageResponse<FailedJob> failedJobs(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        actor.requireApprovedTeacher();
        PageQuery query = PageQuery.of(page, size);
        List<FailedJob> items = jdbc.sql("""
                        select id, type, aggregate_id, attempts, last_error, updated_at from background_jobs
                        where status = :failed order by updated_at desc, id limit :limit offset :offset
                        """)
                .param("failed", JobStatus.FAILED.code())
                .param("limit", query.size())
                .param("offset", query.offset())
                .query((rs, row) -> new FailedJob(rs.getObject("id", UUID.class), rs.getString("type"), rs.getObject("aggregate_id", UUID.class),
                        rs.getInt("attempts"), rs.getString("last_error"), rs.getTimestamp("updated_at").toInstant()))
                .list();
        long total = jdbc.sql("select count(*) from background_jobs where status = :failed").param("failed", JobStatus.FAILED.code()).query(Long.class).single();
        return PageResponse.of(items, query, total);
    }


    record SystemStatus(boolean emailConfigured, long failedJobs) {
    }

    record FailedJob(UUID id, String type, UUID aggregateId, int attempts, String lastError, Instant failedAt) {
    }
}
