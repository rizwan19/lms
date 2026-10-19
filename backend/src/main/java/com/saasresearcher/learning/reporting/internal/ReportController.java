package com.saasresearcher.learning.reporting.internal;

import com.saasresearcher.learning.classrooms.api.TeachingAccess;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.media.api.AssetStore;
import com.saasresearcher.learning.media.api.AssetStream;
import com.saasresearcher.learning.media.api.ByteRange;
import com.saasresearcher.learning.reporting.api.ReportType;
import com.saasresearcher.learning.reporting.api.StudentProgressRow;
import com.saasresearcher.learning.reporting.api.StudentScoreRow;
import com.saasresearcher.learning.reporting.internal.ExportRepository.ExportRow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Staff reports; students never call these routes. */
@RestController
@RequestMapping("/reports")
class ReportController {

    private final ClassReports reports;
    private final ExportRepository exports;
    private final TeachingAccess teaching;
    private final AssetStore store;

    ReportController(ClassReports reports, ExportRepository exports, TeachingAccess teaching, AssetStore store) {
        this.reports = reports;
        this.exports = exports;
        this.teaching = teaching;
        this.store = store;
    }

    @GetMapping("/classes/{id}/progress")
    PageResponse<StudentProgressRow> progress(@PathVariable UUID id, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, Actor actor) {
        return reports.classProgress(id, actor, PageQuery.of(page, size));
    }

    @GetMapping("/classes/{id}/scores")
    PageResponse<StudentScoreRow> scores(@PathVariable UUID id, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, Actor actor) {
        return reports.classScores(id, actor, PageQuery.of(page, size));
    }

    @PostMapping("/classes/{id}/export")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ExportView export(@PathVariable UUID id, @RequestBody ExportRequest request, Actor actor) {
        return ExportView.of(exports.find(reports.requestExport(id, request.type(), actor)).orElseThrow());
    }

    @GetMapping("/classes/{id}/exports")
    List<ExportView> myExports(@PathVariable UUID id, Actor actor) {
        teaching.requireClassTeacher(id, actor);
        return exports.ofRequester(actor.userId(), id).stream().map(ExportView::of).toList();
    }

    /** Only the requester, and only while they still teach the class. */
    @GetMapping("/exports/{id}/download")
    ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id, Actor actor) throws IOException {
        ExportRow export = exports.find(id).filter(row -> row.requestedBy().equals(actor.userId())).orElseThrow(ApiException::notFound);
        teaching.requireClassTeacher(export.classId(), actor);
        if (!export.status().equals("READY")) {
            throw ApiException.conflict("EXPORT_NOT_READY", "The export is not ready");
        }
        long size = store.stat(export.storageKey()).sizeBytes();
        StreamingResponseBody body = output -> {
            try (AssetStream stream = store.openRange(export.storageKey(), ByteRange.whole(size))) {
                stream.content().transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .contentLength(size)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.type().name().toLowerCase() + "-" + export.createdAt().toString().substring(0, 10) + ".csv").build().toString())
                .body(body);
    }

    record ExportRequest(ReportType type) {
    }

    record ExportView(UUID id, ReportType type, String status, Instant createdAt) {

        static ExportView of(ExportRow row) {
            return new ExportView(row.id(), row.type(), row.status(), row.createdAt());
        }
    }
}
