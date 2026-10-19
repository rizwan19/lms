package com.saasresearcher.learning.operations.internal;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.operations.internal.MaintenanceService.State;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/maintenance")
class MaintenanceController {

    private final MaintenanceService maintenance;

    MaintenanceController(MaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @GetMapping
    State get(Actor actor) {
        if (!actor.isAdministrator()) {
            throw ApiException.accessDenied("Administrator role required");
        }
        return maintenance.state();
    }

    @PutMapping
    State set(@RequestBody Change request, Actor actor) {
        return maintenance.set(request.active(), request.reason(), actor);
    }

    record Change(boolean active, String reason) {
    }
}
