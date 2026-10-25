package com.saasresearcher.learning.identity.repository;

import com.saasresearcher.learning.identity.entity.UserAccount;
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

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {

    String ACTIVE_APPROVED_TEACHER = "u.status = com.saasresearcher.learning.identity.api.AccountStatus.ACTIVE"
            + " and u.role = com.saasresearcher.learning.common.Role.TEACHER"
            + " and u.teacherStatus = com.saasresearcher.learning.common.TeacherStatus.APPROVED and u.emailVerifiedAt is not null";

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<UserAccount> findByIdIn(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> lockById(@Param("id") UUID id);

    /** {@code FOR SHARE} in UUID order, the global lock order for accounts. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select u from UserAccount u where u.id in :ids order by u.id")
    List<UserAccount> lockSharedInOrder(@Param("ids") Collection<UUID> ids);

    /** {@code FOR UPDATE} in UUID order, used by approval and suspension after the workspace lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id in :ids order by u.id")
    List<UserAccount> lockInOrder(@Param("ids") Collection<UUID> ids);

    @Query("select count(u) from UserAccount u where " + ACTIVE_APPROVED_TEACHER + " and u.id <> :excluded")
    long countOtherActiveApprovedTeachers(@Param("excluded") UUID excludedId);

    @Query("select u.id from UserAccount u where " + ACTIVE_APPROVED_TEACHER + " order by u.id")
    List<UUID> activeApprovedTeacherIds();
}
