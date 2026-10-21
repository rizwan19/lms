package com.saasresearcher.learning.identity.config;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.WebConfiguration;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import com.saasresearcher.learning.identity.service.IdentityErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-reads the signed-in account on every request so disabling, role changes and password resets
 * take effect immediately, and enforces the password-change gate after an assisted reset.
 */
public class ActiveAccountFilter extends OncePerRequestFilter {

    static final String ACTOR_ATTRIBUTE = ActiveAccountFilter.class.getName() + ".actor";

    private static final Set<String> PASSWORD_CHANGE_ALLOWED = Set.of(
            "GET " + WebConfiguration.API_BASE + "/auth/me",
            "GET " + WebConfiguration.API_BASE + "/auth/csrf",
            "PUT " + WebConfiguration.API_BASE + "/auth/me/password",
            "POST " + WebConfiguration.API_BASE + "/auth/logout",
            "GET " + WebConfiguration.API_BASE + "/institution");

    private final UserAccountRepository accounts;
    private final ProblemWriter problems;

    ActiveAccountFilter(UserAccountRepository accounts, ProblemWriter problems) {
        this.accounts = accounts;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof UsernamePasswordAuthenticationToken)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<UserAccount> account = accounts.findById(UUID.fromString(authentication.getName())).filter(UserAccount::isActive);
        if (account.isEmpty()) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            chain.doFilter(request, response);
            return;
        }
        if (account.get().passwordChangeRequired()
                && !PASSWORD_CHANGE_ALLOWED.contains(request.getMethod() + " " + request.getRequestURI())) {
            problems.write(response, HttpStatus.FORBIDDEN, IdentityErrors.PASSWORD_CHANGE_REQUIRED, "Change your password to continue");
            return;
        }
        Actor actor = account.get().toActor();
        request.setAttribute(ACTOR_ATTRIBUTE, actor);
        chain.doFilter(request, response);
    }
}
