package com.saasresearcher.learning.workspace.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.workspace.api.WorkspaceService;
import com.saasresearcher.learning.workspace.api.WorkspaceUpdateRequest;
import com.saasresearcher.learning.workspace.dto.WorkspaceView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspace")
public class WorkspaceController {

    private final WorkspaceService workspace;

    WorkspaceController(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @GetMapping
    WorkspaceView get() {
        return WorkspaceView.of(workspace.getWorkspaceData());
    }

    /** Approved teachers only. */
    @PutMapping
    WorkspaceView update(@Valid @RequestBody WorkspaceUpdateRequest request, Actor actor) {
        return WorkspaceView.of(workspace.update(request, actor));
    }
}
