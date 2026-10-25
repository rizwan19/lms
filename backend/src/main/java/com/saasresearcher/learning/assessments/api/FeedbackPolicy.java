package com.saasresearcher.learning.assessments.api;

import java.util.Arrays;

public enum FeedbackPolicy {
    SCORE_AND_COMMENTS(0),
    AFTER_CLOSE(1),
    AFTER_SUBMISSION(2);

    private final short code;

    FeedbackPolicy(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static FeedbackPolicy fromCode(short code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown feedback policy " + code));
    }
}
