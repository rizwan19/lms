package com.saasresearcher.learning.identity.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.identity.api.AccountStatus;
import com.saasresearcher.learning.identity.api.TemporaryCredential;
import com.saasresearcher.learning.identity.dto.AccountViews.AccountView;
import com.saasresearcher.learning.identity.dto.CredentialDelivery;
import com.saasresearcher.learning.identity.service.AccountRecoveryService;
import com.saasresearcher.learning.identity.service.AccountService.AccountChanges;
import com.saasresearcher.learning.identity.service.AccountService.CreatedAccount;
import com.saasresearcher.learning.identity.service.AccountService.NewAccount;
import com.saasresearcher.learning.identity.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class AccountController {

    private final AccountService accounts;
    private final AccountRecoveryService recovery;

    AccountController(AccountService accounts, AccountRecoveryService recovery) {
        this.accounts = accounts;
        this.recovery = recovery;
    }

    @GetMapping
    PageResponse<AccountView> list(@RequestParam(required = false) String search, @RequestParam(required = false) Role role,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        return PageResponse.of(accounts.list(search, role, PageQuery.of(page, size), actor), AccountView::of);
    }

    /** Responses may carry a one-time credential, so they are never cached. */
    @PostMapping
    ResponseEntity<CreatedAccountView> create(@Valid @RequestBody CreateAccount request, Actor actor) {
        CreatedAccount created = accounts.create(new NewAccount(request.username(), request.displayName(), request.email(),
                request.roles(), request.delivery()), actor);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new CreatedAccountView(AccountView.of(created.account()), created.temporaryCredential(), created.invitationExpiresAt()));
    }

    @GetMapping("/{id}")
    AccountView get(@PathVariable UUID id, Actor actor) {
        return AccountView.of(accounts.get(id, actor));
    }

    @PutMapping("/{id}")
    AccountView update(@PathVariable UUID id, @Valid @RequestBody UpdateAccount request, Actor actor) {
        return AccountView.of(accounts.update(id, new AccountChanges(request.displayName(), request.email(), request.roles(),
                request.expectedRevision()), actor));
    }

    @PutMapping("/{id}/status")
    AccountView setStatus(@PathVariable UUID id, @Valid @RequestBody StatusChange request, Actor actor) {
        return AccountView.of(accounts.setStatus(id, request.status(), actor));
    }

    @PostMapping("/{id}/assisted-reset")
    ResponseEntity<TemporaryCredential> assistedReset(@PathVariable UUID id, Actor actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(recovery.assistedReset(id, actor));
    }

    record CreateAccount(@NotNull String username, @NotNull String displayName, String email, @NotNull Set<Role> roles,
            @NotNull CredentialDelivery delivery) {
    }

    record UpdateAccount(@NotNull String displayName, String email, @NotNull Set<Role> roles, long expectedRevision) {
    }

    record StatusChange(@NotNull AccountStatus status) {
    }

    record CreatedAccountView(AccountView account, TemporaryCredential temporaryCredential, Instant invitationExpiresAt) {
    }
}
