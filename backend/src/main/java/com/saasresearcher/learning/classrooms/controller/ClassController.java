package com.saasresearcher.learning.classrooms.controller;

import com.saasresearcher.learning.classrooms.api.EnrollmentStatus;
import com.saasresearcher.learning.classrooms.dto.ClassViews.ClassDetail;
import com.saasresearcher.learning.classrooms.dto.ClassViews.ClassSummary;
import com.saasresearcher.learning.classrooms.dto.ClassViews.RosterRow;
import com.saasresearcher.learning.classrooms.service.ClassService;
import com.saasresearcher.learning.classrooms.service.EnrollmentService;
import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
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

@RestController
@RequestMapping("/classes")
public class ClassController {

    private final ClassService classes;
    private final EnrollmentService enrollments;

    ClassController(ClassService classes, EnrollmentService enrollments) {
        this.classes = classes;
        this.enrollments = enrollments;
    }

    @GetMapping
    PageResponse<ClassSummary> list(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        return classes.list(PageQuery.of(page, size), actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClassDetail create(@Valid @RequestBody NewClass request, Actor actor) {
        return classes.create(request.courseVersionId(), request.name(), request.startsAt(), request.endsAt(), actor);
    }

    @GetMapping("/{id}")
    ClassDetail get(@PathVariable UUID id, Actor actor) {
        return classes.get(id, actor);
    }

    @PutMapping("/{id}")
    ClassDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateClass request, Actor actor) {
        return classes.update(id, request.name(), request.startsAt(), request.endsAt(), request.expectedRevision(), actor);
    }

    @PostMapping("/{id}/activate")
    ClassDetail activate(@PathVariable UUID id, Actor actor) {
        return classes.activate(id, actor);
    }

    @PostMapping("/{id}/archive")
    ClassDetail archive(@PathVariable UUID id, Actor actor) {
        return classes.archive(id, actor);
    }

    @PutMapping("/{id}/teachers")
    ClassDetail setTeachers(@PathVariable UUID id, @Valid @RequestBody Teachers request, Actor actor) {
        return classes.setTeachers(id, request.teacherIds(), actor);
    }

    @GetMapping("/{id}/enrollments")
    PageResponse<RosterRow> roster(@PathVariable UUID id, @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        return enrollments.roster(id, status, PageQuery.of(page, size), actor);
    }

    @PostMapping("/{id}/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    RosterRow enroll(@PathVariable UUID id, @Valid @RequestBody Enroll request, Actor actor) {
        return enrollments.enroll(id, request.username(), actor);
    }

    @PutMapping("/{id}/enrollments/{enrollmentId}")
    RosterRow changeStatus(@PathVariable UUID id, @PathVariable UUID enrollmentId, @Valid @RequestBody StatusChange request, Actor actor) {
        return enrollments.changeStatus(id, enrollmentId, request.status(), actor);
    }

    record NewClass(@NotNull UUID courseVersionId, @NotNull String name, Instant startsAt, Instant endsAt) {
    }

    record UpdateClass(@NotNull String name, Instant startsAt, Instant endsAt, long expectedRevision) {
    }

    record Teachers(@NotNull Set<UUID> teacherIds) {
    }

    record Enroll(@NotNull String username) {
    }

    record StatusChange(@NotNull EnrollmentStatus status) {
    }
}
