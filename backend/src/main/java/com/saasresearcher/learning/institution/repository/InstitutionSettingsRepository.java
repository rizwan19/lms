package com.saasresearcher.learning.institution.repository;

import com.saasresearcher.learning.institution.entity.InstitutionSettingsRecord;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InstitutionSettingsRepository extends JpaRepository<InstitutionSettingsRecord, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InstitutionSettingsRecord s where s.id = 1")
    Optional<InstitutionSettingsRecord> lockSingleton();
}
