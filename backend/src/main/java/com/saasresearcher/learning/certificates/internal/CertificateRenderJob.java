package com.saasresearcher.learning.certificates.internal;

import com.saasresearcher.learning.certificates.api.CertificateStatus;
import com.saasresearcher.learning.certificates.internal.CertificateRenderer.UnsupportedTextException;
import com.saasresearcher.learning.media.api.AssetStore;
import com.saasresearcher.learning.media.api.MediaCatalog;
import com.saasresearcher.learning.media.api.UploadSource;
import com.saasresearcher.learning.notifications.api.JobHandler;
import com.saasresearcher.learning.notifications.api.JobRequest;
import com.saasresearcher.learning.notifications.api.NotificationRequest;
import com.saasresearcher.learning.notifications.api.Notifications;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Renders outside any transaction, stores the PDF under the certificate's fixed key (a retry after a crash overwrites
 * the same object), then rechecks the status under the row lock: a revocation that arrived meanwhile wins.
 */
@Component
class CertificateRenderJob implements JobHandler {

    private final CertificateRepository certificates;
    private final CertificateRenderer renderer;
    private final AssetStore store;
    private final MediaCatalog media;
    private final Notifications notifications;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String publicOrigin;

    CertificateRenderJob(CertificateRepository certificates, CertificateRenderer renderer, AssetStore store, MediaCatalog media,
            Notifications notifications, TransactionTemplate transactions, Clock clock, @Value("${learning.public-origin}") String publicOrigin) {
        this.certificates = certificates;
        this.renderer = renderer;
        this.store = store;
        this.media = media;
        this.notifications = notifications;
        this.transactions = transactions;
        this.clock = clock;
        this.publicOrigin = publicOrigin;
    }

    @Override
    public String type() {
        return CertificateIssueService.RENDER_JOB;
    }

    @Override
    public void handle(JobRequest job) throws IOException {
        CertificateRow certificate = certificates.find(job.aggregateId()).orElseThrow();
        if (certificate.status() != CertificateStatus.PENDING_RENDER) {
            return;
        }
        CertificateSnapshot snapshot = certificate.snapshot();
        byte[] pdf;
        try {
            pdf = renderer.render(certificate.id(), snapshot, snapshot.verificationEnabled() ? publicOrigin + "/verify/" + snapshot.verificationToken() : null,
                    logo(snapshot));
        } catch (UnsupportedTextException unsupported) {
            transactions.executeWithoutResult(status -> certificates.lock(certificate.id())
                    .filter(current -> current.status() == CertificateStatus.PENDING_RENDER)
                    .ifPresent(current -> certificates.setStatus(current.id(), CertificateStatus.RENDER_FAILED, unsupported.getMessage(), clock.instant())));
            return;
        }
        store.put(new UploadSource(certificate.storageKey(), new ByteArrayInputStream(pdf)));
        transactions.executeWithoutResult(status -> {
            CertificateRow current = certificates.lock(certificate.id()).orElseThrow();
            if (current.status() == CertificateStatus.PENDING_RENDER) {
                certificates.markIssued(current.id(), clock.instant());
                notifications.notify(new NotificationRequest(current.studentId(), "certificate.issued", "Your certificate is ready",
                        snapshot.courseTitle(), "/student/certificates", "certificate-issued:" + current.id()));
            } else {
                certificates.markRendered(current.id(), clock.instant());
            }
        });
    }

    private byte[] logo(CertificateSnapshot snapshot) throws IOException {
        if (snapshot.logoAssetId() == null) {
            return null;
        }
        try (InputStream in = media.open(snapshot.logoAssetId())) {
            return in.readAllBytes();
        }
    }
}
