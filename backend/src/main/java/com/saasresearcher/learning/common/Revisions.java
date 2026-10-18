package com.saasresearcher.learning.common;

/** Optimistic concurrency check for client-supplied expected revisions. */
public final class Revisions {

    private Revisions() {
    }

    public static void require(long expected, long actual) {
        if (expected != actual) {
            throw ApiException.conflict(ErrorCodes.REVISION_CONFLICT, "The record was changed by another request; reload and retry");
        }
    }
}
