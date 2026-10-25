package com.saasresearcher.learning.identity.controller;

import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.identity.dto.AccountViews.RegistrationReceipt;
import com.saasresearcher.learning.identity.service.RegistrationService;
import com.saasresearcher.learning.identity.service.RegistrationService.RegisterCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class RegistrationController {

    private final RegistrationService registration;

    RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    /** Public signup as TEACHER or STUDENT; the response never includes a session or token. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegistrationReceipt register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return registration.register(new RegisterCommand(request.username(), request.displayName(), request.email(), request.password(),
                request.role()), http.getRemoteAddr());
    }

    record RegisterRequest(@NotNull String username, @NotNull String displayName, @NotNull String email, @NotNull String password,
            @NotNull Role role) {
    }
}
