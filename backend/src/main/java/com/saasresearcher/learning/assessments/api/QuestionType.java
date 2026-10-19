package com.saasresearcher.learning.assessments.api;

public enum QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_SELECT,
    TRUE_FALSE,
    WRITTEN;

    public boolean isObjective() {
        return this != WRITTEN;
    }
}
