package com.saasresearcher.learning.media.api;

import java.io.InputStream;

public record UploadSource(String key, InputStream content) {
}
