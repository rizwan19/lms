package com.saasresearcher.learning.certificates.controller;

import com.saasresearcher.learning.certificates.api.CertificateStatus;
import com.saasresearcher.learning.certificates.api.PublicCertificateStatus;
import com.saasresearcher.learning.certificates.entity.Certificate;
import com.saasresearcher.learning.certificates.service.CertificateIssueService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.media.api.AssetStore;
import com.saasresearcher.learning.media.api.AssetStream;
import com.saasresearcher.learning.media.api.ByteRange;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class CertificateController {

    private final CertificateIssueService certificates;
    private final AssetStore store;

    CertificateController(CertificateIssueService certificates, AssetStore store) {
        this.certificates = certificates;
        this.store = store;
    }

    @GetMapping("/me/certificates")
    List<CertificateView> mine(Actor actor) {
        return certificates.ofLearner(actor.userId()).stream().map(CertificateView::of).toList();
    }

    /** The course author's list. */
    @GetMapping("/courses/{id}/certificates")
    List<CertificateView> ofCourse(@PathVariable UUID id, Actor actor) {
        return certificates.ofCourse(id, actor).stream().map(CertificateView::of).toList();
    }

    /** The learner (also after withdrawal) or the course author only. Never cached by shared proxies. */
    @GetMapping("/certificates/{id}/download")
    ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id, Actor actor) throws IOException {
        Certificate certificate = certificates.requireReadable(id, actor);
        if (certificate.getStatus() != CertificateStatus.ISSUED) {
            throw ApiException.conflict("NOT_ISSUED", "This certificate is not available for download");
        }
        long size = store.stat(certificate.getStorageKey()).sizeBytes();
        StreamingResponseBody body = output -> {
            try (AssetStream stream = store.openRange(certificate.getStorageKey(), ByteRange.whole(size))) {
                stream.content().transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(size)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("certificate-" + id + ".pdf").build().toString())
                .body(body);
    }

    @PostMapping("/certificates/{id}/revoke")
    CertificateView revoke(@PathVariable UUID id, @RequestBody Reason request, Actor actor) {
        certificates.revoke(id, request.reason(), actor);
        return CertificateView.of(certificates.require(id));
    }

    @PostMapping("/certificates/{id}/reinstate")
    CertificateView reinstate(@PathVariable UUID id, @RequestBody Reason request, Actor actor) {
        certificates.reinstate(id, request.reason(), actor);
        return CertificateView.of(certificates.require(id));
    }

    @PostMapping("/certificates/{id}/retry")
    CertificateView retry(@PathVariable UUID id, Actor actor) {
        certificates.retry(id, actor);
        return CertificateView.of(certificates.require(id));
    }

    @GetMapping("/public/certificates/{token}")
    PublicCertificateStatus verify(@PathVariable String token) {
        return certificates.verify(token);
    }

    record Reason(String reason) {
    }

    /** No verification token here: it exists only inside the PDF. */
    record CertificateView(UUID id, UUID courseId, String learnerName, String courseTitle, String authorName, int edition, LocalDate completedOn,
            CertificateStatus status, Instant issuedAt, String renderError, String revokedReason) {

        static CertificateView of(Certificate certificate) {
            return new CertificateView(certificate.getId(), certificate.getCourseId(), certificate.getLearnerName(), certificate.getCourseTitle(),
                    certificate.getAuthorName(), certificate.getEdition(), certificate.getCompletedOn(), certificate.getStatus(), certificate.getIssuedAt(),
                    certificate.getRenderError(), certificate.getRevokedReason());
        }
    }
}
