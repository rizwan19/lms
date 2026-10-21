package com.saasresearcher.learning.media.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("learning.media")
public record MediaProperties(
        @DefaultValue("data/media") Path storageRoot,
        @DefaultValue("data/staging") Path stagingDir,
        @DefaultValue("ffprobe") String ffprobePath,
        @DefaultValue("500MB") DataSize maxVideoSize,
        @DefaultValue("25MB") DataSize maxDocumentSize,
        @DefaultValue("5MB") DataSize maxImageSize,
        @DefaultValue("24h") Duration abandonedUploadAfter,
        @DefaultValue("7d") Duration orphanAfter) {
}
