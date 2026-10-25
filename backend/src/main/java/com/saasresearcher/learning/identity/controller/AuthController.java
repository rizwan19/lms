package com.saasresearcher.learning.identity.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.identity.dto.AccountViews.ProfileView;
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

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final LoginService loginService;
    private final AccountService accountService;
    private final AccountRecoveryService accountRecoveryService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping("/csrf")
    CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/login")
    ProfileView login(@Valid @RequestBody LoginRequest login, HttpServletRequest request, HttpServletResponse response) {
        UserAccount account = loginService.login(login.username(), login.password(), request.getRemoteAddr());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(account.getId().toString(), null, List.of());
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return ProfileView.of(account);
    }

    @GetMapping("/me")
    ProfileView getProfile(Actor actor) {
        return ProfileView.of(accountService.findAccount(actor.userId()));
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody ChangePassword command, Actor actor, HttpServletRequest request) {
        accountRecoveryService.changePassword(actor, command.currentPassword(), command.newPassword(), request.getSession().getId());
    }

    record CsrfView(String headerName, String token) {
    }

    record LoginRequest(@NotBlank @Size(max = 254) String username, @NotNull @Size(max = 128) String password) {
    }

    record ChangePassword(@NotNull String currentPassword, @NotNull String newPassword) {
    }
}
