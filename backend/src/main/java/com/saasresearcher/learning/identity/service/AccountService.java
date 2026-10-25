package com.saasresearcher.learning.identity.service;

import com.saasresearcher.learning.audit.api.AuditEntry;
import com.saasresearcher.learning.audit.api.AuditService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.ErrorCodes;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.Revisions;
import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.common.RowLocks;
import com.saasresearcher.learning.identity.api.AccountStatus;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import com.saasresearcher.learning.identity.api.TokenPurpose;
import com.saasresearcher.learning.identity.entity.UserAccount;
import com.saasresearcher.learning.identity.repository.UserAccountRepository;
import com.saasresearcher.learning.workspace.api.WorkspaceService;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService implements IdentityAccess {

    private final UserAccountRepository userAccountRepository;
    private final WorkspaceService workspaceService;
    private final CredentialIssuer credentialIssuer;
    private final SessionRevocation sessionRevocation;
    private final AuditService auditService;
    private final Clock clock;
    private final RowLocks locks;

    @Override
    @Transactional(readOnly = true)
    public Actor requireActive(UUID userId) {
        return userAccountRepository.findById(userId)
                .filter(UserAccount::isActive)
                .map(UserAccount::toActor)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, "Sign in required"));
    }

    @Override
    @Transactional
    public Map<UUID, AccountSummary> lockShared(Collection<UUID> userIds) {
        Map<UUID, AccountSummary> locked = new LinkedHashMap<>();
        userAccountRepository.lockSharedInOrder(Set.copyOf(userIds)).forEach(account -> locked.put(account.getId(), locks.forShare(account).toSummary()));
        return locked;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AccountSummary> summaries(Collection<UUID> userIds) {
        return userAccountRepository.findByIdIn(userIds).stream().collect(Collectors.toMap(UserAccount::getId, UserAccount::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountSummary> findByEmail(String email) {
        return userAccountRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).map(UserAccount::toSummary);
    }

    @Transactional(readOnly = true)
    public UserAccount findAccount(UUID id) {
        return userAccountRepository.findById(id).orElseThrow(ApiException::notFound);
    }

    @Transactional(readOnly = true)
    public Page<UserAccount> list(String search, Role role, AccountStatus status, PageQuery query, Actor actor) {
        actor.requireApprovedTeacher();
        Specification<UserAccount> spec = (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(root.get("username"), pattern), cb.like(cb.lower(root.get("displayName")), pattern),
                        cb.like(root.get("email"), pattern)));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return userAccountRepository.findAll(spec, query.pageable(Sort.by("username")));
    }

    @Transactional(readOnly = true)
    public UserAccount get(UUID id, Actor actor) {
        actor.requireApprovedTeacher();
        return findAccount(id);
    }

    /**
     * Suspends or reactivates an account. Locks the workspace, then both accounts in UUID order, and rechecks the
     * actor, so two teachers suspending each other cannot leave the workspace without an approved teacher. Nobody may
     * suspend themselves, which keeps the last approved teacher in place.
     */
    @Transactional
    public UserAccount setStatus(UUID id, AccountStatus status, long expectedRevision, Actor actor) {
        actor.requireApprovedTeacher();
        if (id.equals(actor.userId())) {
            throw ApiException.conflict(IdentityErrors.SELF_SUSPENSION, "You cannot change the status of your own account");
        }
        workspaceService.lockWorkspace();
        Map<UUID, UserAccount> locked = userAccountRepository.lockInOrder(Set.of(id, actor.userId())).stream().map(locks::forUpdate)
                .collect(Collectors.toMap(UserAccount::getId, account -> account));
        UserAccount reviewer = locked.get(actor.userId());
        if (reviewer == null || !reviewer.isActiveApprovedTeacher()) {
            throw ApiException.teacherApprovalRequired();
        }
        UserAccount account = Optional.ofNullable(locked.get(id)).orElseThrow(ApiException::notFound);
        Revisions.require(expectedRevision, account.getVersion());
        if (account.getStatus() != status) {
            account.setStatus(status, clock.instant());
            if (status == AccountStatus.SUSPENDED) {
                sessionRevocation.revokeAfterCommit(account.getId());
            }
            userAccountRepository.flush();
            auditService.record(AuditEntry.success(actor.userId(), "account.status-changed", "user", account.getId(), Map.of("status", status)));
        }
        return account;
    }

    /** Sends a recovery link to the account's verified email; nobody but the owner ever chooses the new password. */
    @Transactional
    public void sendPasswordReset(UUID id, Actor actor) {
        actor.requireApprovedTeacher();
        UserAccount account = findAccount(id);
        if (!account.isActive() || !account.isEmailVerified()) {
            throw ApiException.conflict(IdentityErrors.INVALID_TOKEN, "Only active accounts with a verified email can be recovered");
        }
        credentialIssuer.issue(account, TokenPurpose.RESET_PASSWORD);
        auditService.record(AuditEntry.success(actor.userId(), "account.recovery-sent", "user", account.getId()));
    }
}
