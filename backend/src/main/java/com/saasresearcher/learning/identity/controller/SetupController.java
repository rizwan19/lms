package com.saasresearcher.learning.identity.controller;

import com.saasresearcher.learning.common.Timezones;
import com.saasresearcher.learning.identity.dto.AccountViews.MeView;
import com.saasresearcher.learning.identity.service.SetupService.SetupCommand;
import com.saasresearcher.learning.identity.service.SetupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/setup")
public class SetupController {

    private final SetupService setup;

    SetupController(SetupService setup) {
        this.setup = setup;
    }

    @GetMapping
    SetupStatus status() {
        return new SetupStatus(setup.isSetupRequired());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MeView setup(@Valid @RequestBody SetupRequest request) {
        return MeView.of(setup.setup(new SetupCommand(request.setupToken(), request.institutionName(),
                Timezones.parse("timezone", request.timezone()), request.username(), request.displayName(),
                request.email(), request.password())));
    }

    record SetupStatus(boolean required) {
    }

    record SetupRequest(@NotBlank String setupToken, @NotBlank @Size(max = 200) String institutionName,
            @NotBlank String timezone, @NotNull String username, @NotNull String displayName,
            @NotNull String email, @NotNull String password) {
    }
}
