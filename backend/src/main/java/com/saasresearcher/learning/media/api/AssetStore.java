package com.saasresearcher.learning.media.api;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

/** Private object storage. Implementations stream; memory use never scales with object size. */
public interface AssetStore {

    StoredAsset put(UploadSource source) throws IOException;

    AssetStat stat(String key) throws IOException;

    AssetStream openRange(String key, ByteRange range) throws IOException;

    void delete(String key) throws IOException;

    /** Fails when the store cannot be reached or written; used by readiness checks. */
    void checkAvailable() throws IOException;

    /**
     * A short-lived signed URL for direct delivery after authorization, when the store supports it (S3). A URL already
     * issued stays valid until it expires, even if access is withdrawn meanwhile.
     */
    default Optional<URI> presignedRead(String key, String contentType, String contentDisposition) {
        return Optional.empty();
    }
}
