package com.saasresearcher.learning.media.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface MediaCatalog {

    /** Returns metadata of an AVAILABLE asset; rejects missing, rejected or still-processing assets. */
    AssetMetadata requireAvailable(UUID assetId);

    /** Opens an available asset for server-side use (e.g. embedding a logo); callers must not expose it unauthorized. */
    InputStream open(UUID assetId) throws IOException;
}
