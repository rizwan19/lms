package com.saasresearcher.learning.identity.internal;

import com.saasresearcher.learning.common.Role;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserAccountRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<UserAccount> findByIdIn(Collection<UUID> ids);

    @Query("select count(u) > 0 from UserAccount u where :role member of u.roles")
    boolean existsWithRole(@Param("role") Role role);

    /** Locks every active administrator so last-administrator checks cannot race. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.status = com.saasresearcher.learning.identity.api.AccountStatus.ACTIVE"
            + " and com.saasresearcher.learning.common.Role.ADMINISTRATOR member of u.roles")
    List<UserAccount> lockActiveAdministrators();
}
