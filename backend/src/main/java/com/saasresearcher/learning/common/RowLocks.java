package com.saasresearcher.learning.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RowLocks {

    private final EntityManager entityManager;

    public <T> T forUpdate(T entity) {
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity;
    }

    public <T> T bumpVersion(T entity) {
        entityManager.lock(entity, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        return entity;
    }

    public <T> T reload(T entity) {
        entityManager.refresh(entity);
        return entity;
    }

    public <T> T forShare(T entity) {
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_READ);
        return entity;
    }
}
