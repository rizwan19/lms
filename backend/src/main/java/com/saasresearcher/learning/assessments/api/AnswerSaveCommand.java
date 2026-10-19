package com.saasresearcher.learning.assessments.api;

import java.util.Set;
import java.util.UUID;

/** Exactly one representation applies: option IDs for objective questions, text for written ones. */
public record AnswerSaveCommand(long expectedRevision, Set<UUID> selectedOptionIds, String writtenText) {
}
