package com.saasresearcher.learning.certificates.internal;

import com.saasresearcher.learning.certificates.api.CertificateStatus;
import com.saasresearcher.learning.certificates.api.PublicCertificateStatus;
import com.saasresearcher.learning.classrooms.api.TeachingAccess;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
class CertificateController {

    private final CertificateIssueService certificates;
    private final CertificateRepository repository;
    private final TeachingAccess teaching;
    private final AssetStore store;

    CertificateController(CertificateIssueService certificates, CertificateRepository repository, TeachingAccess teaching, AssetStore store) {
        this.certificates = certificates;
        this.repository = repository;
        this.teaching = teaching;
        this.store = store;
    }

    @GetMapping("/me/certificates")
    List<CertificateView> mine(Actor actor) {
        return repository.ofStudent(actor.userId()).stream().map(CertificateView::of).toList();
    }

    @GetMapping("/certificates")
    List<CertificateView> ofClass(@RequestParam UUID classId, Actor actor) {
        teaching.requireClassTeacher(classId, actor);
        return repository.ofClass(classId).stream().map(CertificateView::of).toList();
    }

    /** Owners can download after withdrawal; staff only for classes they teach. Never cached by shared proxies. */
    @GetMapping("/certificates/{id}/download")
    ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id, Actor actor) throws IOException {
        CertificateRow certificate = certificates.requireReadable(id, actor);
        if (certificate.status() != CertificateStatus.ISSUED) {
            throw ApiException.conflict("NOT_ISSUED", "This certificate is not available for download");
        }
        long size = store.stat(certificate.storageKey()).sizeBytes();
        StreamingResponseBody body = output -> {
            try (AssetStream stream = store.openRange(certificate.storageKey(), ByteRange.whole(size))) {
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
        return CertificateView.of(repository.find(id).orElseThrow());
    }

    @PostMapping("/certificates/{id}/reinstate")
    CertificateView reinstate(@PathVariable UUID id, @RequestBody Reason request, Actor actor) {
        certificates.reinstate(id, request.reason(), actor);
        return CertificateView.of(repository.find(id).orElseThrow());
    }

    @PostMapping("/certificates/{id}/retry")
    CertificateView retry(@PathVariable UUID id, Actor actor) {
        certificates.retry(id, actor);
        return CertificateView.of(repository.find(id).orElseThrow());
    }

    @GetMapping("/public/certificates/{token}")
    PublicCertificateStatus verify(@PathVariable String token) {
        return certificates.verify(token);
    }

    record Reason(String reason) {
    }

    /** No verification token here: it exists only inside the PDF. */
    record CertificateView(UUID id, String studentName, String courseTitle, int edition, LocalDate completedOn, CertificateStatus status,
            Instant issuedAt, String renderError, String revokedReason) {

        static CertificateView of(CertificateRow row) {
            return new CertificateView(row.id(), row.snapshot().studentName(), row.snapshot().courseTitle(), row.snapshot().edition(),
                    row.snapshot().completedOn(), row.status(), row.issuedAt(), row.renderError(), row.revokedReason());
        }
    }
}
