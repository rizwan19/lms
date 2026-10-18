package com.saasresearcher.learning.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Business failure rendered as an RFC 9457 problem response with a stable {@code code}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Map<String, String> fieldErrors;

    public ApiException(HttpStatus status, String code, String detail, Map<String, String> fieldErrors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public ApiException(HttpStatus status, String code, String detail) {
        this(status, code, detail, Map.of());
    }

    /** Unknown or unrelated resource; also used for forbidden reads to avoid exposing existence. */
    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "Resource not found");
    }

    public static ApiException accessDenied(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.ACCESS_DENIED, detail);
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiException invalid(String field, String message) {
        return invalid(Map.of(field, message));
    }

    public static ApiException invalid(Map<String, String> fieldErrors) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed", fieldErrors);
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
