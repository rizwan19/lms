package com.saasresearcher.learning.media.service;

import org.springframework.http.HttpStatus;

/** An upload that failed validation; the asset is marked REJECTED with this reason. */
public class RejectedUpload extends Exception {

    static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    static final String UPLOAD_REJECTED = "UPLOAD_REJECTED";
    static final String UPLOAD_INTERRUPTED = "UPLOAD_INTERRUPTED";

    final HttpStatus status;
    final String code;

    RejectedUpload(HttpStatus status, String code, String reason) {
        super(reason);
        this.status = status;
        this.code = code;
    }

    static RejectedUpload invalid(String reason) {
        return new RejectedUpload(HttpStatus.UNPROCESSABLE_CONTENT, UPLOAD_REJECTED, reason);
    }

    static RejectedUpload tooLarge(long maxBytes) {
        return new RejectedUpload(HttpStatus.CONTENT_TOO_LARGE, FILE_TOO_LARGE, "File exceeds the " + (maxBytes / (1024 * 1024)) + " MiB limit");
    }
}
