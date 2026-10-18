package com.saasresearcher.learning.media.internal;

import com.saasresearcher.learning.media.api.AssetReferences;
import com.saasresearcher.learning.media.api.AssetStore;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Expires abandoned uploads after 24 hours and removes unreferenced assets after the 7-day grace period. */
@Component
public class MediaMaintenance {

    private final MediaAssetRepository assets;
    private final AssetStore store;
    private final UploadService uploads;
    private final ObjectProvider<AssetReferences> references;
    private final MediaProperties properties;
    private final Clock clock;

    MediaMaintenance(MediaAssetRepository assets, AssetStore store, UploadService uploads, ObjectProvider<AssetReferences> references,
            MediaProperties properties, Clock clock) {
        this.assets = assets;
        this.store = store;
        this.uploads = uploads;
        this.references = references;
        this.properties = properties;
        this.clock = clock;
    }

    public void sweep() throws IOException {
        Instant now = clock.instant();
        for (MediaAsset abandoned : assets.findByStatusInAndUpdatedAtBefore(
                List.of(MediaAsset.Status.UPLOADING, MediaAsset.Status.VALIDATING), now.minus(properties.abandonedUploadAfter()))) {
            UploadValidator.deleteQuietly(uploads.staging(abandoned));
            store.delete(abandoned.storageKey());
            abandoned.reject("Upload abandoned", now);
            assets.save(abandoned);
        }
        for (MediaAsset candidate : assets.findByStatusInAndUpdatedAtBefore(List.of(MediaAsset.Status.AVAILABLE), now.minus(properties.orphanAfter()))) {
            if (references.stream().noneMatch(reference -> reference.isReferenced(candidate.id()))) {
                store.delete(candidate.storageKey());
                assets.delete(candidate);
            }
        }
    }
}
