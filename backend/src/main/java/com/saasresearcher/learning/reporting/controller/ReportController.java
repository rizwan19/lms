package com.saasresearcher.learning.reporting.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.media.api.AssetStore;
import com.saasresearcher.learning.media.api.AssetStream;
import com.saasresearcher.learning.media.api.ByteRange;
import com.saasresearcher.learning.reporting.api.ExportStatus;
import com.saasresearcher.learning.reporting.api.LearnerProgressRow;
import com.saasresearcher.learning.reporting.api.LearnerScoreRow;
import com.saasresearcher.learning.reporting.api.ReportType;
import com.saasresearcher.learning.reporting.entity.ReportExport;
import com.saasresearcher.learning.reporting.service.CourseReports;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

/** Course reports for the course's author; learners read only their own history elsewhere. */
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final CourseReports reports;
    private final AssetStore store;

    ReportController(CourseReports reports, AssetStore store) {
        this.reports = reports;
        this.store = store;
    }

    @GetMapping("/courses/{id}/progress")
    PageResponse<LearnerProgressRow> progress(@PathVariable UUID id, @RequestParam(required = false) UUID versionId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        return reports.courseProgress(id, versionId, actor, PageQuery.of(page, size));
    }

    @GetMapping("/courses/{id}/scores")
    PageResponse<LearnerScoreRow> scores(@PathVariable UUID id, @RequestParam(required = false) UUID versionId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        return reports.courseScores(id, versionId, actor, PageQuery.of(page, size));
    }

    @PostMapping("/courses/{id}/export")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ExportView export(@PathVariable UUID id, @Valid @RequestBody ExportRequest request, Actor actor) {
        return ExportView.of(reports.export(reports.requestExport(id, request.versionId(), request.type(), actor)).orElseThrow());
    }

    @GetMapping("/courses/{id}/exports")
    List<ExportView> myExports(@PathVariable UUID id, Actor actor) {
        return reports.exportsOf(id, actor).stream().map(ExportView::of).toList();
    }

    /** Only the requester, and only while they are still the course's author. */
    @GetMapping("/exports/{id}/download")
    ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id, Actor actor) throws IOException {
        ReportExport export = reports.export(id).filter(row -> row.getRequestedBy().equals(actor.userId())).orElseThrow(ApiException::notFound);
        reports.requireRequesterStillAuthor(export, actor);
        if (export.getStatus() != ExportStatus.READY) {
            throw ApiException.conflict("EXPORT_NOT_READY", "The export is not ready");
        }
        long size = store.stat(export.getStorageKey()).sizeBytes();
        StreamingResponseBody body = output -> {
            try (AssetStream stream = store.openRange(export.getStorageKey(), ByteRange.whole(size))) {
                stream.content().transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .contentLength(size)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.getType().name().toLowerCase() + "-" + export.getCreatedAt().toString().substring(0, 10) + ".csv").build().toString())
                .body(body);
    }

    record ExportRequest(@NotNull ReportType type, UUID versionId) {
    }

    record ExportView(UUID id, ReportType type, UUID versionId, ExportStatus status, Instant createdAt) {

        static ExportView of(ReportExport row) {
            return new ExportView(row.getId(), row.getType(), row.getCourseVersionId(), row.getStatus(), row.getCreatedAt());
        }
    }
}
