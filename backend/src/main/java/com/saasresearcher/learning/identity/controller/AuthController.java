package com.saasresearcher.learning.identity.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.identity.dto.AccountViews.MeView;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.service.AccountRecoveryService;
import com.saasresearcher.learning.identity.service.AccountService;
import com.saasresearcher.learning.identity.service.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LoginService loginService;
    private final AccountService accounts;
    private final AccountRecoveryService recovery;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository securityContexts;

    AuthController(LoginService loginService, AccountService accounts, AccountRecoveryService recovery,
            SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository securityContexts) {
        this.loginService = loginService;
        this.accounts = accounts;
        this.recovery = recovery;
        this.sessionStrategy = sessionStrategy;
        this.securityContexts = securityContexts;
    }

    /** Loads the CSRF token so the cookie is issued; the SPA echoes it in X-XSRF-TOKEN. */
    @GetMapping("/csrf")
    CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/login")
    MeView login(@Valid @RequestBody LoginRequest login, HttpServletRequest request, HttpServletResponse response) {
        UserAccount account = loginService.login(login.username(), login.password(), request.getRemoteAddr());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(account.id().toString(), null, List.of());
        sessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContexts.saveContext(context, request, response);
        return MeView.of(account);
    }

    @GetMapping("/me")
    MeView me(Actor actor) {
        return MeView.of(accounts.findAccount(actor.userId()));
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody ChangePassword command, Actor actor, HttpServletRequest request) {
        recovery.changePassword(actor, command.currentPassword(), command.newPassword(), request.getSession().getId());
    }

    record CsrfView(String headerName, String token) {
    }

    record LoginRequest(@NotBlank @Size(max = 254) String username, @NotNull @Size(max = 128) String password) {
    }

    record ChangePassword(@NotNull String currentPassword, @NotNull String newPassword) {
    }
}
