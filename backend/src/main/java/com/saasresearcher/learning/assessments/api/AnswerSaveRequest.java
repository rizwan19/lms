package com.saasresearcher.learning.assessments.api;

import java.util.Set;
import java.util.UUID;

public record AnswerSaveRequest(Set<UUID> selectedOptionIds, String writtenText) {
}
