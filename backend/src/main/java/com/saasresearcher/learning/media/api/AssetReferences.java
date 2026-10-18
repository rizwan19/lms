package com.saasresearcher.learning.media.api;

import java.util.UUID;

/** Reports whether a module still links an asset; unreferenced assets are removed after the orphan grace period. */
public interface AssetReferences {

    boolean isReferenced(UUID assetId);
}
