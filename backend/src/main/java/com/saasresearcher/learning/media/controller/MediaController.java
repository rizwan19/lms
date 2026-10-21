package com.saasresearcher.learning.media.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.media.api.AssetMetadata;
import com.saasresearcher.learning.media.api.AssetPurpose;
import com.saasresearcher.learning.media.api.AssetStore;
import com.saasresearcher.learning.media.api.AssetStream;
import com.saasresearcher.learning.media.api.AuthorizedAsset;
import com.saasresearcher.learning.media.api.ByteRange;
import com.saasresearcher.learning.media.api.MediaService;
import com.saasresearcher.learning.media.entity.MediaAsset;
import com.saasresearcher.learning.media.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/media")
public class MediaController {

    private final UploadService uploads;
    private final MediaService media;
    private final AssetStore store;

    MediaController(UploadService uploads, MediaService media, AssetStore store) {
        this.uploads = uploads;
        this.media = media;
        this.store = store;
    }

    /** Raw-body upload: the request body is the file, streamed without buffering in memory. */
    @PostMapping
    ResponseEntity<AssetMetadata> upload(@RequestParam AssetPurpose purpose, @RequestParam String filename,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType, HttpServletRequest request, Actor actor) throws IOException {
        MediaAsset asset = uploads.upload(purpose, filename, contentType, request.getContentLengthLong(), request.getInputStream(), actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(asset.toMetadata());
    }

    @GetMapping("/{id}")
    AssetMetadata metadata(@PathVariable UUID id, Actor actor) {
        return media.authorizeRead(id, actor).metadata();
    }

    /** Authorized private delivery with single-range support (206/416); never cached by shared proxies. */
    @GetMapping("/{id}/content")
    ResponseEntity<StreamingResponseBody> content(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean download,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader, Actor actor) throws IOException {
        AuthorizedAsset asset = media.authorizeRead(id, actor);
        HttpHeaders headers = deliveryHeaders(asset.metadata(), download);
        Optional<URI> signed = store.presignedRead(asset.storageKey(), headers.getFirst(HttpHeaders.CONTENT_TYPE),
                headers.getFirst(HttpHeaders.CONTENT_DISPOSITION));
        if (signed.isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(signed.get()).cacheControl(CacheControl.noStore()).build();
        }
        long size = store.stat(asset.storageKey()).sizeBytes();
        ByteRange range = ByteRange.whole(size);
        HttpStatus status = HttpStatus.OK;
        if (rangeHeader != null) {
            try {
                List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
                range = new ByteRange(ranges.getFirst().getRangeStart(size), ranges.getFirst().getRangeEnd(size));
                if (range.start() >= size || range.end() < range.start()) {
                    throw new IllegalArgumentException("Range outside the file");
                }
                status = HttpStatus.PARTIAL_CONTENT;
                headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + range.start() + "-" + range.end() + "/" + size);
            } catch (IllegalArgumentException e) {
                headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).headers(headers).build();
            }
        }
        headers.setContentLength(range.length());
        ByteRange selected = range;
        StreamingResponseBody body = output -> {
            try (AssetStream stream = store.openRange(asset.storageKey(), selected)) {
                stream.content().transferTo(output);
            }
        };
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    private static HttpHeaders deliveryHeaders(AssetMetadata metadata, boolean download) {
        HttpHeaders headers = new HttpHeaders();
        String type = metadata.contentType().equals("text/plain") ? "text/plain;charset=UTF-8" : metadata.contentType();
        headers.set(HttpHeaders.CONTENT_TYPE, type);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CACHE_CONTROL, "private, no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'self'");
        headers.setContentDisposition((download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(metadata.filename(), StandardCharsets.UTF_8).build());
        return headers;
    }
}
