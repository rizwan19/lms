package com.saasresearcher.learning.notifications.internal;

import com.saasresearcher.learning.notifications.api.DurableJobs;
import com.saasresearcher.learning.notifications.api.JobRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** SQL for the background_jobs lifecycle: PENDING → RUNNING → SUCCEEDED, or back to PENDING / FAILED. */
@Repository
class JobStore implements DurableJobs {

    private final JdbcClient jdbc;
    private final Clock clock;
    private final JobsProperties properties;

    JobStore(JdbcClient jdbc, Clock clock, JobsProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(JobRequest request) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.sql("""
                insert into background_jobs (id, type, aggregate_id, deduplication_key, payload, status, available_at, created_at, updated_at)
                values (:id, :type, :aggregateId, :key, cast(:payload as jsonb), 'PENDING', :availableAt, :now, :now)
                on conflict (deduplication_key) do nothing
                returning id
                """)
                .param("id", UUID.randomUUID())
                .param("type", request.type())
                .param("aggregateId", request.aggregateId())
                .param("key", request.deduplicationKey())
                .param("payload", request.payloadJson())
                .param("availableAt", Timestamp.from(request.availableAt()))
                .param("now", now)
                .query(UUID.class)
                .optional()
                .orElseGet(() -> jdbc.sql("select id from background_jobs where deduplication_key = :key")
                        .param("key", request.deduplicationKey())
                        .query(UUID.class)
                        .single());
    }

    /** Claims due jobs and expired leases; concurrent claimers skip each other's locked rows. */
    @Transactional
    List<ClaimedJob> claim(int limit) {
        Instant now = clock.instant();
        UUID leaseToken = UUID.randomUUID();
        jdbc.sql("""
                update background_jobs set status = 'FAILED', lease_until = null, lease_token = null, updated_at = :now,
                    last_error = 'Lease expired during the final attempt'
                where status = 'RUNNING' and lease_until < :now and attempts >= :maxAttempts
                """)
                .param("now", Timestamp.from(now))
                .param("maxAttempts", properties.maxAttempts())
                .update();
        return jdbc.sql("""
                with due as (
                    select id from background_jobs
                    where (status = 'PENDING' and available_at <= :now) or (status = 'RUNNING' and lease_until < :now)
                    order by available_at
                    limit :limit
                    for update skip locked)
                update background_jobs job
                set status = 'RUNNING', attempts = job.attempts + 1, lease_until = :leaseUntil, lease_token = :leaseToken, updated_at = :now
                from due where job.id = due.id
                returning job.id, job.type, job.aggregate_id, job.deduplication_key, job.payload::text as payload, job.available_at, job.attempts
                """)
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .param("leaseUntil", Timestamp.from(now.plus(properties.lease())))
                .param("leaseToken", leaseToken)
                .query((rs, row) -> toClaimedJob(rs, leaseToken))
                .list();
    }

    void renewLease(ClaimedJob job) {
        jdbc.sql("update background_jobs set lease_until = :leaseUntil where id = :id and lease_token = :token and status = 'RUNNING'")
                .param("leaseUntil", Timestamp.from(clock.instant().plus(properties.lease())))
                .param("id", job.id())
                .param("token", job.leaseToken())
                .update();
    }

    /** Ignored when the lease was lost to another worker. */
    void complete(ClaimedJob job) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.sql("""
                update background_jobs set status = 'SUCCEEDED', completed_at = :now, updated_at = :now, lease_until = null, lease_token = null
                where id = :id and lease_token = :token and status = 'RUNNING'
                """)
                .param("now", now)
                .param("id", job.id())
                .param("token", job.leaseToken())
                .update();
    }

    void fail(ClaimedJob job, String error) {
        Instant now = clock.instant();
        boolean terminal = job.attempts() >= properties.maxAttempts();
        Instant retryAt = terminal ? now : now.plus(properties.retryDelays().get(job.attempts() - 1));
        jdbc.sql("""
                update background_jobs set status = :status, available_at = :retryAt, last_error = :error, updated_at = :now,
                    lease_until = null, lease_token = null
                where id = :id and lease_token = :token and status = 'RUNNING'
                """)
                .param("status", terminal ? "FAILED" : "PENDING")
                .param("retryAt", Timestamp.from(retryAt))
                .param("error", error.length() > 2000 ? error.substring(0, 2000) : error)
                .param("now", Timestamp.from(now))
                .param("id", job.id())
                .param("token", job.leaseToken())
                .update();
    }

    private static ClaimedJob toClaimedJob(ResultSet rs, UUID leaseToken) throws SQLException {
        JobRequest request = new JobRequest(rs.getString("type"), rs.getObject("aggregate_id", UUID.class),
                rs.getString("deduplication_key"), rs.getString("payload"), rs.getTimestamp("available_at").toInstant());
        return new ClaimedJob(rs.getObject("id", UUID.class), leaseToken, rs.getInt("attempts"), request);
    }
}
