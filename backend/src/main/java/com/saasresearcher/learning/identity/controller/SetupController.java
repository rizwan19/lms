package com.saasresearcher.learning.identity.controller;

import com.saasresearcher.learning.identity.dto.AccountViews.RegistrationReceipt;
import com.saasresearcher.learning.identity.dto.SetupRequest;
import com.saasresearcher.learning.identity.service.SetupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/setup")
public class SetupController {

    private final SetupService setupService;

    @GetMapping
    boolean getState() {
        return setupService.isSetupRequired();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    RegistrationReceipt setup(@Valid @RequestBody SetupRequest request) {
        return setupService.setup(request);
    }
}
