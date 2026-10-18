package com.saasresearcher.learning.courses.api;

import java.util.UUID;

/** Either an uploaded private video asset or an external provider embed identified by provider and video ID. */
public record LessonVideo(UUID assetId, VideoProvider provider, String providerRef) {
}
