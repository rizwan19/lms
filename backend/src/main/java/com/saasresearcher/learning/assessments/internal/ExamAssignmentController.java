package com.saasresearcher.learning.assessments.internal;

import com.saasresearcher.learning.assessments.internal.ExamAssignmentService.AssignmentView;
import com.saasresearcher.learning.assessments.internal.ExamAssignmentService.ScheduleCommand;
import com.saasresearcher.learning.assessments.internal.ExamRows.ExamSummary;
import com.saasresearcher.learning.assessments.internal.Schedule.FeedbackPolicy;
import com.saasresearcher.learning.assessments.internal.Schedule.ResultPolicy;
import com.saasresearcher.learning.common.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Class exam schedules for assigned class teachers and administrators. */
@RestController
@RequestMapping("/exam-assignments")
class ExamAssignmentController {

    private final ExamAssignmentService assignments;

    ExamAssignmentController(ExamAssignmentService assignments) {
        this.assignments = assignments;
    }

    @GetMapping
    List<AssignmentView> forClass(@RequestParam UUID classId, Actor actor) {
        return assignments.forClass(classId, actor);
    }

    @GetMapping("/available")
    List<ExamSummary> available(@RequestParam UUID classId, Actor actor) {
        return assignments.available(classId, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AssignmentView create(@Valid @RequestBody NewAssignment request, Actor actor) {
        return assignments.createPractice(request.classId(), request.examVersionId(), request.schedule(), actor);
    }

    @PutMapping("/{id}")
    AssignmentView update(@PathVariable UUID id, @RequestBody UpdateAssignment request, Actor actor) {
        return assignments.update(id, request.expectedRevision(), request.schedule(), actor);
    }

    record NewAssignment(@NotNull UUID classId, @NotNull UUID examVersionId, Instant opensAt, Instant closesAt, Integer durationMinutes,
            Integer maxAttempts, ResultPolicy resultPolicy, FeedbackPolicy feedbackPolicy) {

        ScheduleCommand schedule() {
            return new ScheduleCommand(opensAt, closesAt, durationMinutes, maxAttempts, resultPolicy, feedbackPolicy);
        }
    }

    record UpdateAssignment(long expectedRevision, Instant opensAt, Instant closesAt, Integer durationMinutes, Integer maxAttempts,
            ResultPolicy resultPolicy, FeedbackPolicy feedbackPolicy) {

        ScheduleCommand schedule() {
            return new ScheduleCommand(opensAt, closesAt, durationMinutes, maxAttempts, resultPolicy, feedbackPolicy);
        }
    }
}
