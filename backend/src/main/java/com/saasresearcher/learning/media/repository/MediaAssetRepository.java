package com.saasresearcher.learning.media.repository;

import com.saasresearcher.learning.media.api.AssetStatus;
import com.saasresearcher.learning.media.entity.MediaAsset;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    List<MediaAsset> findByStatusInAndUpdatedAtBefore(Collection<AssetStatus> statuses, Instant before);
}
