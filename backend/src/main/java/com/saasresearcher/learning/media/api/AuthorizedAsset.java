package com.saasresearcher.learning.media.api;

/** An asset the caller may read right now; the storage key never leaves the server. */
public record AuthorizedAsset(AssetMetadata metadata, String storageKey) {
}
