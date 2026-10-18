package com.saasresearcher.learning.media.internal;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.media.api.AssetAccessPolicy;
import com.saasresearcher.learning.media.api.AssetMetadata;
import com.saasresearcher.learning.media.api.AuthorizedAsset;
import com.saasresearcher.learning.media.api.MediaCatalog;
import com.saasresearcher.learning.media.api.MediaService;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default deny: administrators, the uploader and registered linking policies may read an available asset. */
@Service
class MediaAccessService implements MediaService, MediaCatalog {

    private final MediaAssetRepository assets;
    private final ObjectProvider<AssetAccessPolicy> policies;

    MediaAccessService(MediaAssetRepository assets, ObjectProvider<AssetAccessPolicy> policies) {
        this.assets = assets;
        this.policies = policies;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizedAsset authorizeRead(UUID assetId, Actor actor) {
        MediaAsset asset = assets.findById(assetId).filter(MediaAsset::isAvailable).orElseThrow(ApiException::notFound);
        boolean allowed = actor.isAdministrator() || asset.uploadedBy().equals(actor.userId())
                || policies.stream().anyMatch(policy -> policy.canRead(assetId, actor));
        if (!allowed) {
            throw ApiException.notFound();
        }
        return new AuthorizedAsset(asset.toMetadata(), asset.storageKey());
    }

    @Override
    @Transactional(readOnly = true)
    public AssetMetadata requireAvailable(UUID assetId) {
        return assets.findById(assetId)
                .filter(MediaAsset::isAvailable)
                .map(MediaAsset::toMetadata)
                .orElseThrow(() -> ApiException.invalid("assetId", "The file is missing or not available yet"));
    }
}
