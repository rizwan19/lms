package com.saasresearcher.learning.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.saasresearcher.learning.notifications.api.DurableJobs;
import com.saasresearcher.learning.notifications.api.EmailMessage;
import com.saasresearcher.learning.notifications.api.JobRequest;
import com.saasresearcher.learning.notifications.api.NotificationRequest;
import com.saasresearcher.learning.notifications.api.Notifications;
import com.saasresearcher.learning.notifications.infra.JobRunner;
import com.saasresearcher.learning.support.ApiClient.ApiResponse;
import com.saasresearcher.learning.support.ApiClient;
import com.saasresearcher.learning.support.PostgresIntegrationTest;
import com.saasresearcher.learning.support.TestJobsConfiguration.RecordingJobHandler;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class JobRecoveryIT extends PostgresIntegrationTest {

    @Autowired
    private DurableJobs jobs;

    @Autowired
    private Notifications notifications;

    @Autowired
    private JobRunner runner;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void rollbackRemovesTheEnqueuedJob() {
        try {
            transactions.executeWithoutResult(status -> {
                jobs.enqueue(request("rolled-back"));
                throw new IllegalStateException("business failure");
            });
        } catch (IllegalStateException expected) {
            // the business transaction failed
        }
        assertThat(jobCount()).isZero();
    }

    @Test
    void duplicateDeduplicationKeysReturnTheExistingJob() {
        UUID first = transactions.execute(status -> jobs.enqueue(request("same-key")));
        UUID second = transactions.execute(status -> jobs.enqueue(request("same-key")));

        assertThat(second).isEqualTo(first);
        assertThat(jobCount()).isEqualTo(1);
    }

    @Test
    void competingWorkersClaimEachJobOnce() throws Exception {
        transactions.executeWithoutResult(status -> {
            for (int i = 0; i < 30; i++) {
                jobs.enqueue(request("job-" + i));
            }
        });
        CyclicBarrier barrier = new CyclicBarrier(3);
        List<Future<?>> workers = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            for (int worker = 0; worker < 3; worker++) {
                workers.add(pool.submit(() -> {
                    barrier.await();
                    runner.drain();
                    return null;
                }));
            }
            for (Future<?> future : workers) {
                future.get();
            }
        }

        List<String> keys = recordingJobs.handled().stream().map(JobRequest::deduplicationKey).toList();
        assertThat(keys).hasSize(30).doesNotHaveDuplicates();
        assertThat(jdbc.sql("select count(*) from background_jobs where status = 'SUCCEEDED' and attempts = 1").query(Long.class).single()).isEqualTo(30);
    }

    @Test
    void crashedLeaseIsReclaimedWithTheSameJobIdentity() {
        UUID jobId = transactions.execute(status -> jobs.enqueue(request("crash")));
        // A worker claims the job and dies before acknowledging it.
        jdbc.sql("update background_jobs set status = 'RUNNING', attempts = 1, lease_until = :lease, lease_token = :token where id = :id")
                .param("lease", Timestamp.from(clock.instant().plusSeconds(60)))
                .param("token", UUID.randomUUID())
                .param("id", jobId)
                .update();

        assertThat(runner.runOnce()).isZero();
        clock.advance(Duration.ofSeconds(61));
        assertThat(runner.runOnce()).isEqualTo(1);

        assertThat(recordingJobs.handled()).extracting(JobRequest::deduplicationKey).containsExactly("crash");
        Map<String, Object> row = jdbc.sql("select id, status, attempts from background_jobs").query().singleRow();
        assertThat(row).containsEntry("id", jobId).containsEntry("status", "SUCCEEDED").containsEntry("attempts", 2);
    }

    @Test
    void fifthFailedExecutionIsTerminalAndVisibleToAdministrators() {
        ApiClient admin = setupAdministrator();
        recordingJobs.failNext(10);
        transactions.executeWithoutResult(status -> jobs.enqueue(request("always-fails")));

        List<Duration> delays = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));
        for (Duration delay : delays) {
            runner.drain();
            assertThat(status()).isEqualTo("PENDING");
            clock.advance(delay.minusSeconds(1));
            assertThat(runner.runOnce()).as("not due before the retry delay").isZero();
            clock.advance(Duration.ofSeconds(1));
        }
        runner.drain();

        assertThat(status()).isEqualTo("FAILED");
        assertThat(recordingJobs.handled()).hasSize(5);
        ApiResponse failed = admin.get("/admin/failed-jobs").expect(200);
        assertThat(failed.json().path("items").get(0).path("attempts").asInt()).isEqualTo(5);
        assertThat(admin.get("/admin/system-status").json().path("failedJobs").asLong()).isEqualTo(1);
        clock.advance(Duration.ofDays(1));
        assertThat(runner.runOnce()).isZero();
    }

    @Test
    void duplicateInAppNotificationsAreSuppressed() {
        ApiClient admin = setupAdministrator();
        UUID adminId = UUID.fromString(admin.get("/auth/me").json().path("id").asString());
        NotificationRequest notice = new NotificationRequest(adminId, "test", "Welcome", "Hello", "/", "welcome:" + adminId);

        transactions.executeWithoutResult(status -> notifications.notify(notice));
        transactions.executeWithoutResult(status -> notifications.notify(notice));

        ApiResponse list = admin.get("/notifications?unreadOnly=true");
        assertThat(list.json().path("total").asLong()).isEqualTo(1);
        String id = list.json().path("items").get(0).path("id").asString();
        admin.put("/notifications/" + id + "/read", null).expect(204);
        assertThat(admin.get("/notifications?unreadOnly=true").json().path("total").asLong()).isZero();

        TestUser other = createUser(admin, "other.user", null, "STUDENT");
        assertThat(other.session().put("/notifications/" + id + "/read", null).status()).isEqualTo(404);
    }

    @Test
    void smtpFailureNeverUndoesTheCommittedAccountAndRetryDeliversAWorkingLink() {
        ApiClient admin = setupAdministrator();
        email.failing(true);
        ApiResponse created = admin.post("/users", Map.of("username", "invitee", "displayName", "Invitee",
                "email", "invitee@example.test", "roles", List.of("TEACHER"), "delivery", "INVITATION")).expect(201);

        runner.drain();
        assertThat(status()).isEqualTo("PENDING");
        assertThat(admin.get("/users/" + created.json().path("account").path("id").asString()).status()).isEqualTo(200);

        email.failing(false);
        clock.advance(Duration.ofMinutes(1));
        runner.drain();
        assertThat(status()).isEqualTo("SUCCEEDED");
        EmailMessage message = email.sent().getFirst();
        assertThat(message.to()).isEqualTo("invitee@example.test");
        String token = message.body().replaceAll("(?s).*accept-invitation\\?token=(\\S+).*", "$1");
        assertThat(jdbc.sql("select count(*) from background_jobs where payload::text like :token").param("token", "%" + token + "%")
                .query(Long.class).single()).isZero();

        ApiClient invitee = client();
        invitee.get("/auth/csrf");
        invitee.post("/auth/invitations/accept", Map.of("token", token, "password", PASSWORD)).expect(204);
        assertThat(login("invitee", PASSWORD).get("/auth/me").status()).isEqualTo(200);
        assertThat(jdbc.sql("select count(*) from audit_events where action = 'invitation.accepted'").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void passwordResetEmailIsQueuedOnlyForAccountsWithEmail() {
        ApiClient admin = setupAdministrator();
        createUser(admin, "with.email", "with@example.test", "STUDENT");
        createUser(admin, "without.email", null, "STUDENT");
        ApiClient anonymous = client();
        anonymous.get("/auth/csrf");

        anonymous.post("/auth/password-reset/request", Map.of("usernameOrEmail", "with.email")).expect(202);
        anonymous.post("/auth/password-reset/request", Map.of("usernameOrEmail", "without.email")).expect(202);
        runner.drain();

        assertThat(email.sent()).extracting(EmailMessage::to).containsExactly("with@example.test");
        String token = email.sent().getFirst().body().replaceAll("(?s).*reset-password\\?token=(\\S+).*", "$1");
        anonymous.post("/auth/password-reset/confirm", Map.of("token", token, "password", "a fresh long passphrase")).expect(204);
        assertThat(login("with.email", "a fresh long passphrase").get("/auth/me").status()).isEqualTo(200);
    }

    private JobRequest request(String key) {
        return new JobRequest(RecordingJobHandler.TYPE, UUID.randomUUID(), key, "{}", clock.instant());
    }

    private long jobCount() {
        return jdbc.sql("select count(*) from background_jobs").query(Long.class).single();
    }

    private String status() {
        return jdbc.sql("select status from background_jobs").query(String.class).single();
    }

}
