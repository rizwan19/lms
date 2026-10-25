package com.saasresearcher.learning.operations.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.operations.service.MaintenanceService.State;
import com.saasresearcher.learning.operations.service.MaintenanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenance;

    MaintenanceController(MaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @GetMapping
    State get(Actor actor) {
        actor.requireApprovedTeacher();
        return maintenance.state();
    }

    @PutMapping
    State set(@RequestBody Change request, Actor actor) {
        return maintenance.set(request.active(), request.reason(), actor);
    }

    record Change(boolean active, String reason) {
    }
}
