package com.saasresearcher.learning.learning.dto;

import com.saasresearcher.learning.learning.entity.LessonProgress;
import java.time.Instant;
import java.util.UUID;

public record ProgressView(UUID lessonId, boolean completed, Instant completedAt, long resumeSeconds, long revision, Instant lastActivityAt) {

    public static ProgressView of(LessonProgress progress) {
        return new ProgressView(progress.getLessonId(), progress.completed(), progress.getCompletedAt(), progress.getResumeSeconds(), progress.getRevision(),
                progress.getLastActivityAt());
    }
}
