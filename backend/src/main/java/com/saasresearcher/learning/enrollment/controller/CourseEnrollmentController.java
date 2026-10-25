package com.saasresearcher.learning.enrollment.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.enrollment.api.EnrollmentStatus;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.MyEnrollment;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.RosterRow;
import com.saasresearcher.learning.enrollment.service.EnrollmentAccessServiceImpl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseEnrollmentController {

    private final EnrollmentAccessServiceImpl enrollments;

    CourseEnrollmentController(EnrollmentAccessServiceImpl enrollments) {
        this.enrollments = enrollments;
    }

    @GetMapping("/me/enrollments")
    List<MyEnrollment> mine(Actor actor) {
        return enrollments.mine(actor);
    }

    @PostMapping("/me/enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@PathVariable UUID id, Actor actor) {
        enrollments.withdraw(id, actor);
    }

    /** The author's roster. */
    @GetMapping("/courses/{id}/enrollments")
    PageResponse<RosterRow> roster(@PathVariable UUID id, @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        return enrollments.roster(id, status, PageQuery.of(page, size), actor);
    }

    /** The author suspends (SUSPENDED) or restores (ACTIVE) a learner. */
    @PutMapping("/enrollments/{id}/status")
    RosterRow changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusChange request, Actor actor) {
        return enrollments.changeStatus(id, request.status(), request.expectedRevision(), actor);
    }

    record StatusChange(@NotNull EnrollmentStatus status, long expectedRevision) {
    }
}
