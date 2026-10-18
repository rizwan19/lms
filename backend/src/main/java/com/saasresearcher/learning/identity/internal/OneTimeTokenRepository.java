package com.saasresearcher.learning.identity.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
interface OneTimeTokenRepository<T extends OneTimeToken> extends JpaRepository<T, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<T> findByTokenHash(String tokenHash);
}
