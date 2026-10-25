package com.saasresearcher.learning.enrollment.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.enrollment.api.InvitationStatus;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.AcceptedInvitation;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.BulkInvitationResult;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.InvitationView;
import com.saasresearcher.learning.enrollment.service.CourseInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseInvitationController {

    private final CourseInvitationService invitations;

    CourseInvitationController(CourseInvitationService invitations) {
        this.invitations = invitations;
    }

    @PostMapping("/courses/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationView invite(@PathVariable UUID id, @Valid @RequestBody Invite request, Actor actor) {
        return invitations.invite(id, request.email(), actor);
    }

    @PostMapping("/courses/{id}/invitations/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    BulkInvitationResult inviteBulk(@PathVariable UUID id, @Valid @RequestBody BulkInvite request, Actor actor) {
        return invitations.inviteBulk(id, request.emails(), actor);
    }

    @GetMapping("/courses/{id}/invitations")
    PageResponse<InvitationView> list(@PathVariable UUID id, @RequestParam(required = false) InvitationStatus status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        return invitations.list(id, status, PageQuery.of(page, size), actor);
    }

    @PostMapping("/course-invitations/{id}/revoke")
    InvitationView revoke(@PathVariable UUID id, Actor actor) {
        return invitations.revoke(id, actor);
    }

    /** The signed-in, verified recipient accepts explicitly; following the email link alone never enrolls anyone. */
    @PostMapping("/course-invitations/accept")
    AcceptedInvitation accept(@Valid @RequestBody Accept request, Actor actor) {
        return invitations.accept(request.token(), actor);
    }

    record Invite(@NotNull String email) {
    }

    /** Duplicates collapse after normalization; more than 100 different addresses are rejected. */
    record BulkInvite(@NotNull @Size(max = 500) List<String> emails) {
    }

    record Accept(@NotBlank @Size(max = 200) String token) {
    }
}
