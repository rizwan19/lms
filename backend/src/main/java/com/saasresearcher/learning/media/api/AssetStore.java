package com.saasresearcher.learning.media.api;

import java.io.IOException;

/** Private object storage. Implementations stream; memory use never scales with object size. */
public interface AssetStore {

    StoredAsset put(UploadSource source) throws IOException;

    AssetStat stat(String key) throws IOException;

    AssetStream openRange(String key, ByteRange range) throws IOException;

    void delete(String key) throws IOException;
}
