package com.saasresearcher.learning.media.api;

import java.util.UUID;

public interface MediaCatalog {

    /** Returns metadata of an AVAILABLE asset; rejects missing, rejected or still-processing assets. */
    AssetMetadata requireAvailable(UUID assetId);
}
