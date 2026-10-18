package com.saasresearcher.learning.learning.api;

/** Expected revision 0 creates the record. {@code resumeSeconds} never moves the saved position backwards. */
public record ProgressCommand(long expectedRevision, boolean completed, long resumeSeconds) {
}
