package com.saasresearcher.learning.assessments.entity;

import java.time.Instant;
import java.util.UUID;

public record AssignmentRow(UUID id, UUID classId, UUID examVersionId, boolean required, Schedule schedule, long version) {

    /** Students may start while the assignment is open: after opening and before closing. */
    public boolean isOpen(Instant now) {
        return (schedule.opensAt() == null || !now.isBefore(schedule.opensAt())) && (schedule.closesAt() == null || now.isBefore(schedule.closesAt()));
    }
}
