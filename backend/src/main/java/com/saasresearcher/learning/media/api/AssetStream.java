package com.saasresearcher.learning.media.api;

import java.io.IOException;
import java.io.InputStream;

/** An open stream over {@code range} of an object whose full size is {@code totalSize}. Close it. */
public record AssetStream(InputStream content, ByteRange range, long totalSize) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        content.close();
    }
}
