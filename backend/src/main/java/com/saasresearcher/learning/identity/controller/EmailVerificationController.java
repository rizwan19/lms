package com.saasresearcher.learning.identity.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.identity.service.EmailVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/email")
public class EmailVerificationController {

    private final EmailVerificationService verification;

    EmailVerificationController(EmailVerificationService verification) {
        this.verification = verification;
    }

    /** Token-bound and unauthenticated: opening the link alone changes nothing; the page posts the token. */
    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verify(@Valid @RequestBody TokenRequest request) {
        verification.verify(request.token());
    }

    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resend(Actor actor) {
        verification.resend(actor);
    }

    record TokenRequest(@NotBlank @Size(max = 200) String token) {
    }
}
