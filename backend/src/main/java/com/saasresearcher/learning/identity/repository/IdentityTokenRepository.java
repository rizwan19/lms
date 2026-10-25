package com.saasresearcher.learning.identity.repository;

import com.saasresearcher.learning.identity.api.TokenPurpose;
import com.saasresearcher.learning.identity.entity.IdentityToken;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityTokenRepository extends JpaRepository<IdentityToken, UUID> {

    /** Unlocked lookup; callers lock the user first and then the token by ID, the identity lock order. */
    Optional<IdentityToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from IdentityToken t where t.id = :id")
    Optional<IdentityToken> lockById(@Param("id") UUID id);

    /** Usable-looking tokens of these purposes (expiry is checked by the caller), locked so revocation and delivery serialize. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from IdentityToken t where t.userId = :user and t.purpose in :purposes and t.consumedAt is null and t.revokedAt is null")
    List<IdentityToken> lockOpen(@Param("user") UUID userId, @Param("purposes") Collection<TokenPurpose> purposes);
}
