package com.saasresearcher.learning.identity.internal;

import com.saasresearcher.learning.common.Role;
import com.saasresearcher.learning.identity.api.AccountStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Response shapes for account endpoints. */
final class AccountViews {

    private AccountViews() {
    }

    record MeView(UUID id, String username, String displayName, String email, Set<Role> roles, boolean passwordChangeRequired) {

        static MeView of(UserAccount account) {
            return new MeView(account.id(), account.username(), account.displayName(), account.email(), account.roles(),
                    account.passwordChangeRequired());
        }
    }

    record AccountView(UUID id, String username, String displayName, String email, Set<Role> roles, AccountStatus status,
            Instant createdAt, long revision) {

        static AccountView of(UserAccount account) {
            return new AccountView(account.id(), account.username(), account.displayName(), account.email(), account.roles(),
                    account.status(), account.createdAt(), account.version());
        }
    }
}
