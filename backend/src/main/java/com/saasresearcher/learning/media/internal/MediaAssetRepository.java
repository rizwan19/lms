package com.saasresearcher.learning.media.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    List<MediaAsset> findByStatusInAndUpdatedAtBefore(Collection<MediaAsset.Status> statuses, Instant before);
}
