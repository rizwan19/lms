package com.saasresearcher.learning.identity.config;

import com.saasresearcher.learning.common.ProblemResponses;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes problem responses from security filters, which run before MVC exception handling. */
@Component
public class ProblemWriter {

    private final JsonMapper json;

    ProblemWriter(JsonMapper json) {
        this.json = json;
    }

    void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ProblemResponses.problem(status, code, detail, Map.of()));
    }
}
