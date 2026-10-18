package com.saasresearcher.learning.media.api;

/** Inclusive byte range. */
public record ByteRange(long start, long end) {

    public static ByteRange whole(long size) {
        return new ByteRange(0, Math.max(0, size - 1));
    }

    public long length() {
        return end - start + 1;
    }
}
