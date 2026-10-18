package com.saasresearcher.learning.common;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds the problem body shape {type,title,status,detail,code,fieldErrors,traceId}. */
public final class ProblemResponses {

    private ProblemResponses() {
    }

    public static ProblemDetail problem(HttpStatusCode status, String code, String detail, Map<String, String> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setProperty("code", code);
        problem.setProperty("fieldErrors", fieldErrors);
        problem.setProperty("traceId", TraceIdFilter.current());
        return problem;
    }
}
