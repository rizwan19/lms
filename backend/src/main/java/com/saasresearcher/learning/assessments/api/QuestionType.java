package com.saasresearcher.learning.assessments.api;

import java.util.Arrays;

public enum QuestionType {
    SINGLE_CHOICE(0),
    MULTIPLE_SELECT(1),
    TRUE_FALSE(2),
    WRITTEN(3);

    private final short code;

    QuestionType(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static QuestionType fromCode(short code) {
        return Arrays.stream(values())
                .filter(type -> type.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown question type " + code));
    }

    public boolean isObjective() {
        return this != WRITTEN;
    }
}
