package com.saasresearcher.learning.certificates.infra;

import com.saasresearcher.learning.certificates.api.CertificateStatus;
import com.saasresearcher.learning.certificates.entity.Certificate;
import com.saasresearcher.learning.certificates.infra.CertificateRenderer.UnsupportedTextException;
import com.saasresearcher.learning.certificates.repository.CertificateRepository;
import com.saasresearcher.learning.certificates.service.CertificateIssueService;
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
public class CertificateRenderJob implements JobHandler {

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
        Certificate certificate = certificates.findById(job.aggregateId()).orElseThrow();
        if (certificate.getStatus() != CertificateStatus.PENDING_RENDER) {
            return;
        }
        byte[] pdf;
        try {
            pdf = renderer.render(certificate,
                    certificate.isVerificationEnabled() ? publicOrigin + "/verify/" + certificate.getVerificationToken() : null, logo(certificate));
        } catch (UnsupportedTextException unsupported) {
            transactions.executeWithoutResult(status -> certificates.lockById(certificate.getId())
                    .filter(current -> current.getStatus() == CertificateStatus.PENDING_RENDER)
                    .ifPresent(current -> current.renderFailed(unsupported.getMessage(), clock.instant())));
            return;
        }
        store.put(new UploadSource(certificate.getStorageKey(), new ByteArrayInputStream(pdf)));
        transactions.executeWithoutResult(status -> {
            Certificate current = certificates.lockById(certificate.getId()).orElseThrow();
            if (current.getStatus() == CertificateStatus.PENDING_RENDER) {
                current.markIssued(clock.instant());
                certificates.flush();
                notifications.notify(new NotificationRequest(current.getLearnerId(), "certificate.issued", "Your certificate is ready",
                        certificate.getCourseTitle(), "/me/certificates", "certificate-issued:" + current.getId()));
            } else {
                current.markRendered(clock.instant());
            }
        });
    }

    private byte[] logo(Certificate certificate) throws IOException {
        if (certificate.getLogoAssetId() == null) {
            return null;
        }
        try (InputStream in = media.open(certificate.getLogoAssetId())) {
            return in.readAllBytes();
        }
    }
}
