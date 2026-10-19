package com.saasresearcher.learning.assessments.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Summary of an immutable exam edition; never carries keys. {@code mixed} means it has written questions. */
public record PublishedExam(UUID versionId, UUID examId, UUID courseId, int edition, String title, int questionCount,
        BigDecimal maxPoints, BigDecimal passPercent, boolean mixed) {
}
