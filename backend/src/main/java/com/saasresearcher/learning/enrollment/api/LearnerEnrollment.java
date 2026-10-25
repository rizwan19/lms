package com.saasresearcher.learning.enrollment.api;

import java.time.Instant;

/** A learner's own enrollment, including historical ones; {@code accessOpen} means learning is possible right now. */
public record LearnerEnrollment(EnrollmentView enrollment, Instant grantedAt, Instant statusChangedAt, boolean accessOpen) {
}
