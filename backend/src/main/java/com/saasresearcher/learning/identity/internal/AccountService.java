package com.saasresearcher.learning.identity.internal;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditLog;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.ErrorCodes;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.identity.api.AccountStatus;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.identity.api.TemporaryCredential;
import com.saasresearcher.learning.notifications.api.EmailSender;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountService implements IdentityAccess {

    private final UserAccountRepository accounts;
    private final AccountAuthorization authorization;
    private final CredentialIssuer credentials;
    private final SessionRevocation sessions;
    private final EmailSender email;
    private final AuditLog audit;
    private final Clock clock;

    AccountService(UserAccountRepository accounts, AccountAuthorization authorization, CredentialIssuer credentials,
            SessionRevocation sessions, EmailSender email, AuditLog audit, Clock clock) {
        this.accounts = accounts;
        this.authorization = authorization;
        this.credentials = credentials;
        this.sessions = sessions;
        this.email = email;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Actor requireActive(UUID userId) {
        return accounts.findById(userId)
                .filter(UserAccount::isActive)
                .map(UserAccount::toActor)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, "Sign in required"));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AccountSummary> summaries(Collection<UUID> userIds) {
        return accounts.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(UserAccount::id, UserAccount::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountSummary> findByUsername(String username) {
        return accounts.findByUsername(username.trim().toLowerCase(Locale.ROOT)).map(UserAccount::toSummary);
    }

    @Transactional(readOnly = true)
    UserAccount findAccount(UUID id) {
        return accounts.findById(id).orElseThrow(ApiException::notFound);
    }

    @Transactional
    CreatedAccount create(NewAccount command, Actor actor) {
        authorization.requireStaff(actor);
        Set<Role> roles = requireGrantableRoles(command.roles(), actor);
        String username = AccountRules.username(command.username());
        String email = AccountRules.email(command.email());
        String displayName = AccountRules.displayName(command.displayName());
        requireStaffEmail(roles, email);
        requireUniqueIdentity(username, email, null);

        UserAccount account = new UserAccount(username, email, displayName, roles, actor.userId(), clock.instant());
        TemporaryCredential credential = null;
        Instant invitationExpiresAt = null;
        if (command.delivery() == CredentialDelivery.INVITATION) {
            if (email == null) {
                throw ApiException.invalid("email", "An invitation needs an email address");
            }
            if (!this.email.isConfigured()) {
                throw ApiException.conflict(IdentityErrors.EMAIL_NOT_CONFIGURED, "Email is not configured; use a temporary password instead");
            }
            accounts.save(account);
            invitationExpiresAt = credentials.issueInvitation(account);
        } else {
            credential = credentials.issueTemporaryPassword(account);
            accounts.save(account);
        }
        audit.record(AuditEntry.success(actor.userId(), "account.created", "user", account.id(),
                Map.of("roles", roles, "delivery", command.delivery())));
        return new CreatedAccount(account, credential, invitationExpiresAt);
    }

    @Transactional
    UserAccount update(UUID id, AccountChanges changes, Actor actor) {
        authorization.requireAdministrator(actor);
        UserAccount account = accounts.findById(id).orElseThrow(ApiException::notFound);
        Revisions.require(changes.expectedRevision(), account.version());
        Set<Role> roles = requireGrantableRoles(changes.roles(), actor);
        String email = AccountRules.email(changes.email());
        requireStaffEmail(roles, email);
        requireUniqueIdentity(account.username(), email, account);
        if (!roles.contains(Role.ADMINISTRATOR) && account.isActive() && account.roles().contains(Role.ADMINISTRATOR)) {
            requireAnotherActiveAdministrator(account);
        }
        account.updateProfile(AccountRules.displayName(changes.displayName()), email, roles, clock.instant());
        accounts.flush();
        audit.record(AuditEntry.success(actor.userId(), "account.updated", "user", account.id(), Map.of("roles", roles)));
        return account;
    }

    @Transactional
    UserAccount setStatus(UUID id, AccountStatus status, Actor actor) {
        authorization.requireAdministrator(actor);
        UserAccount account = accounts.findById(id).orElseThrow(ApiException::notFound);
        if (status == AccountStatus.DISABLED && account.isActive()) {
            if (account.roles().contains(Role.ADMINISTRATOR)) {
                requireAnotherActiveAdministrator(account);
            }
            sessions.revokeAfterCommit(account.id());
        }
        account.setStatus(status, clock.instant());
        accounts.flush();
        audit.record(AuditEntry.success(actor.userId(), "account.status-changed", "user", account.id(), Map.of("status", status)));
        return account;
    }

    @Transactional(readOnly = true)
    UserAccount get(UUID id, Actor actor) {
        UserAccount account = findAccount(id);
        authorization.requireManageable(account, actor);
        return account;
    }

    @Transactional(readOnly = true)
    Page<UserAccount> list(String search, Role role, PageQuery query, Actor actor) {
        authorization.requireStaff(actor);
        Specification<UserAccount> spec = (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(root.get("username"), pattern), cb.like(cb.lower(root.get("displayName")), pattern)));
            }
            if (role != null) {
                predicates.add(cb.isMember(role, root.get("roles")));
            }
            if (!actor.isAdministrator()) {
                Set<UUID> managed = authorization.managedStudentIds(actor);
                Predicate created = cb.equal(root.get("createdBy"), actor.userId());
                predicates.add(managed.isEmpty() ? created : cb.or(created, root.get("id").in(managed)));
                predicates.add(cb.isMember(Role.STUDENT, root.get("roles")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return accounts.findAll(spec, query.pageable(Sort.by("username")));
    }

    private static Set<Role> requireGrantableRoles(Set<Role> roles, Actor actor) {
        if (roles == null || roles.isEmpty()) {
            throw ApiException.invalid("roles", "Choose at least one role");
        }
        if (!actor.isAdministrator() && !roles.equals(Set.of(Role.STUDENT))) {
            throw ApiException.accessDenied("Teachers can only create student accounts");
        }
        return Set.copyOf(roles);
    }

    private static void requireStaffEmail(Set<Role> roles, String email) {
        if (email == null && (roles.contains(Role.ADMINISTRATOR) || roles.contains(Role.TEACHER))) {
            throw ApiException.invalid("email", "Teachers and administrators need an email address");
        }
    }

    private void requireUniqueIdentity(String username, String email, UserAccount existing) {
        if (existing == null && accounts.existsByUsername(username)) {
            throw ApiException.conflict(IdentityErrors.USERNAME_TAKEN, "That username is already in use");
        }
        if (email != null && accounts.findByEmail(email).filter(other -> other != existing).isPresent()) {
            throw ApiException.conflict(IdentityErrors.EMAIL_TAKEN, "That email address is already in use");
        }
    }

    private void requireAnotherActiveAdministrator(UserAccount account) {
        boolean anotherRemains = accounts.lockActiveAdministrators().stream().anyMatch(admin -> !admin.id().equals(account.id()));
        if (!anotherRemains) {
            throw ApiException.conflict(IdentityErrors.LAST_ADMINISTRATOR, "The last active administrator cannot be disabled or demoted");
        }
    }

    record NewAccount(String username, String displayName, String email, Set<Role> roles, CredentialDelivery delivery) {
    }

    record AccountChanges(String displayName, String email, Set<Role> roles, long expectedRevision) {
    }

    record CreatedAccount(UserAccount account, TemporaryCredential temporaryCredential, Instant invitationExpiresAt) {
    }
}
