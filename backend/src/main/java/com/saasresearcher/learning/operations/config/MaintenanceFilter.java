package com.saasresearcher.learning.operations.config;

import com.saasresearcher.learning.common.ProblemResponses;
import com.saasresearcher.learning.common.WebConfiguration;
import com.saasresearcher.learning.operations.service.MaintenanceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** During maintenance, reads continue but mutations get 503, except signing in/out and ending maintenance. */
@Component
public class MaintenanceFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
            WebConfiguration.API_BASE + "/auth/login",
            WebConfiguration.API_BASE + "/auth/logout",
            WebConfiguration.API_BASE + "/admin/maintenance");

    /** Resolved lazily: servlet filters are created before the persistence beans the service depends on. */
    private final ObjectProvider<MaintenanceService> maintenance;
    private final JsonMapper json;

    MaintenanceFilter(ObjectProvider<MaintenanceService> maintenance, JsonMapper json) {
        this.maintenance = maintenance;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        return method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS")
                || !request.getRequestURI().startsWith(WebConfiguration.API_BASE) || ALLOWED.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!maintenance.getObject().isActive()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Retry-After", "300");
        json.writeValue(response.getOutputStream(), ProblemResponses.problem(HttpStatus.SERVICE_UNAVAILABLE, "MAINTENANCE",
                "The portal is in maintenance; changes are paused for a few minutes", Map.of()));
    }
}
