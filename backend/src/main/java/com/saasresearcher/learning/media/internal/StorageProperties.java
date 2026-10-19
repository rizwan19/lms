package com.saasresearcher.learning.media.internal;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** Storage selection comes only from configuration: {@code local} (default) or {@code s3}. */
@ConfigurationProperties("learning.storage")
record StorageProperties(@DefaultValue("local") String mode, S3 s3) {

    record S3(URI endpoint, @DefaultValue("us-east-1") String region, String bucket, String accessKey, String secretKey,
            @DefaultValue("false") boolean pathStyle, @DefaultValue("60s") Duration presignTtl, @DefaultValue("8MB") DataSize partSize) {
    }
}
