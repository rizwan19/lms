package com.saasresearcher.learning.media.api;

import java.util.UUID;

public record AssetMetadata(UUID id, AssetPurpose purpose, String filename, String contentType, long sizeBytes, Integer durationSeconds) {
}
