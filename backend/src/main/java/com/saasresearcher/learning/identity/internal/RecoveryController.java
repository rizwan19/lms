package com.saasresearcher.learning.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Token-bound, unauthenticated account recovery and invitation acceptance. */
@RestController
@RequestMapping("/auth")
class RecoveryController {

    private final AccountRecoveryService recovery;

    RecoveryController(AccountRecoveryService recovery) {
        this.recovery = recovery;
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestReset(@Valid @RequestBody ResetRequest command, HttpServletRequest request) {
        recovery.requestPasswordReset(command.usernameOrEmail(), request.getRemoteAddr());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmReset(@Valid @RequestBody TokenPassword command) {
        recovery.confirmPasswordReset(command.token(), command.password());
    }

    @PostMapping("/invitations/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void acceptInvitation(@Valid @RequestBody TokenPassword command) {
        recovery.acceptInvitation(command.token(), command.password());
    }

    record ResetRequest(@NotBlank @Size(max = 254) String usernameOrEmail) {
    }

    record TokenPassword(@NotBlank @Size(max = 200) String token, @NotNull String password) {
    }
}
