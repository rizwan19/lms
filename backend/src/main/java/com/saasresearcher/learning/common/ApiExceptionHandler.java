package com.saasresearcher.learning.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail problem = ProblemResponses.problem(ex.status(), ex.code(), ex.getMessage(), ex.fieldErrors());
        ex.properties().forEach(problem::setProperty);
        return ResponseEntity.status(ex.status()).body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return handleApi(ApiException.conflict(ErrorCodes.REVISION_CONFLICT, "The record was changed by another request; reload and retry"));
    }

    /** Unique-constraint races that slipped past service checks. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        return handleApi(ApiException.conflict(ErrorCodes.DUPLICATE, "The record conflicts with an existing record"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled request failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ProblemResponses.problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "Unexpected server error", Map.of()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ApiException invalid = ApiException.invalid(fieldErrors);
        return ResponseEntity.status(invalid.status())
                .body(ProblemResponses.problem(invalid.status(), invalid.code(), invalid.getMessage(), fieldErrors));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code = status.value() == 404 ? ErrorCodes.NOT_FOUND : ErrorCodes.REQUEST_REJECTED;
        String detail = body instanceof ProblemDetail problem ? problem.getDetail() : ex.getMessage();
        return ResponseEntity.status(status).headers(headers)
                .body(ProblemResponses.problem(status, code, detail, Map.of()));
    }
}
