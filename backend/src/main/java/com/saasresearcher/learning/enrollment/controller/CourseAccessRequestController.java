package com.saasresearcher.learning.enrollment.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.enrollment.api.AccessRequestStatus;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.AccessRequestView;
import com.saasresearcher.learning.enrollment.dto.EnrollmentViews.Decision;
import com.saasresearcher.learning.enrollment.service.CourseAccessRequestService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseAccessRequestController {

    private final CourseAccessRequestService requests;

    CourseAccessRequestController(CourseAccessRequestService requests) {
        this.requests = requests;
    }

    @PostMapping("/courses/{id}/access-requests")
    AccessRequestView request(@PathVariable UUID id, Actor actor) {
        return requests.request(id, actor);
    }

    @GetMapping("/me/access-requests")
    List<AccessRequestView> mine(Actor actor) {
        return requests.mine(actor);
    }

    @PostMapping("/access-requests/{id}/cancel")
    AccessRequestView cancel(@PathVariable UUID id, Actor actor) {
        return requests.cancel(id, actor);
    }

    /** The author's inbox. */
    @GetMapping("/courses/{id}/access-requests")
    PageResponse<AccessRequestView> inbox(@PathVariable UUID id, @RequestParam(defaultValue = "PENDING") AccessRequestStatus status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size, Actor actor) {
        return requests.inbox(id, status, PageQuery.of(page, size), actor);
    }

    @PostMapping("/access-requests/{id}/decision")
    AccessRequestView decide(@PathVariable UUID id, @RequestBody Decision decision, Actor actor) {
        return requests.decide(id, decision, actor);
    }
}
