package com.saasresearcher.learning.identity.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.identity.api.AccountStatus;
import com.saasresearcher.learning.identity.dto.AccountViews.AccountView;
import com.saasresearcher.learning.identity.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Approved-teacher account administration. It never exposes courses, rosters or learning records. */
@RestController
@RequestMapping("/workspace/users")
public class WorkspaceUserController {

    private final AccountService accounts;

    WorkspaceUserController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    PageResponse<AccountView> list(@RequestParam(required = false) String search, @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, Actor actor) {
        return PageResponse.of(accounts.list(search, role, status, PageQuery.of(page, size), actor), AccountView::of);
    }

    @GetMapping("/{id}")
    AccountView get(@PathVariable UUID id, Actor actor) {
        return AccountView.of(accounts.get(id, actor));
    }

    @PutMapping("/{id}/status")
    AccountView setStatus(@PathVariable UUID id, @Valid @RequestBody StatusChange request, Actor actor) {
        return AccountView.of(accounts.setStatus(id, request.status(), request.expectedRevision(), actor));
    }

    /** Emails the user a recovery link; the caller never sees or sets a password. */
    @PostMapping("/{id}/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void sendPasswordReset(@PathVariable UUID id, Actor actor) {
        accounts.sendPasswordReset(id, actor);
    }

    record StatusChange(@NotNull AccountStatus status, long expectedRevision) {
    }
}
