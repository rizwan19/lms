package com.saasresearcher.learning.support;

import java.io.InputStream;

/** Yields {@code prefix} followed by {@code filler} bytes up to {@code size}, without allocating the content. */
public class GeneratedInputStream extends InputStream {

    private final byte[] prefix;
    private final long size;
    private final byte filler;
    private long position;

    public GeneratedInputStream(byte[] prefix, long size, byte filler) {
        this.prefix = prefix;
        this.size = size;
        this.filler = filler;
    }

    @Override
    public int read() {
        if (position >= size) {
            return -1;
        }
        int value = position < prefix.length ? prefix[(int) position] & 0xFF : filler & 0xFF;
        position++;
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
        if (position >= size) {
            return -1;
        }
        int count = (int) Math.min(length, size - position);
        for (int i = 0; i < count; i++) {
            long at = position + i;
            buffer[offset + i] = at < prefix.length ? prefix[(int) at] : filler;
        }
        position += count;
        return count;
    }
}
