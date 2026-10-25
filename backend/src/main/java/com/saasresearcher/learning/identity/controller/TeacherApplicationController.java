package com.saasresearcher.learning.identity.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.identity.api.ApplicationStatus;
import com.saasresearcher.learning.identity.dto.TeacherApplicationViews.ApplicationView;
import com.saasresearcher.learning.identity.dto.TeacherApplicationViews.Decision;
import com.saasresearcher.learning.identity.service.TeacherApplicationService;
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
public class TeacherApplicationController {

    private final TeacherApplicationService applications;

    TeacherApplicationController(TeacherApplicationService applications) {
        this.applications = applications;
    }

    /** Resubmits after a rejection (24-hour cooldown); the first application opens automatically on verification. */
    @PostMapping("/me/teacher-applications")
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationView submit(Actor actor) {
        return applications.submit(actor);
    }

    @GetMapping("/me/teacher-application")
    ApplicationView mine(Actor actor) {
        return applications.mine(actor);
    }

    @GetMapping("/workspace/teacher-applications")
    PageResponse<ApplicationView> inbox(@RequestParam(defaultValue = "PENDING") ApplicationStatus status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, Actor actor) {
        return applications.list(status, PageQuery.of(page, size), actor);
    }

    @PostMapping("/workspace/teacher-applications/{id}/decision")
    ApplicationView decide(@PathVariable UUID id, @RequestBody Decision decision, Actor actor) {
        return applications.decide(id, decision, actor);
    }
}
