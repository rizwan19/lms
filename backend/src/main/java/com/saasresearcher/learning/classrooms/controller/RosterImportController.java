package com.saasresearcher.learning.classrooms.controller;

import com.saasresearcher.learning.classrooms.api.RosterImportPreview;
import com.saasresearcher.learning.classrooms.service.RosterImportService.ImportResult;
import com.saasresearcher.learning.classrooms.service.RosterImportService.ImportRow;
import com.saasresearcher.learning.classrooms.service.RosterImportService;
import com.saasresearcher.learning.common.Actor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/classes/{id}/imports")
public class RosterImportController {

    private final RosterImportService imports;

    RosterImportController(RosterImportService imports) {
        this.imports = imports;
    }

    /** Request body is the raw CSV file (text/csv). */
    @PostMapping("/preview")
    RosterImportPreview preview(@PathVariable UUID id, HttpServletRequest request, Actor actor) throws IOException {
        return imports.preview(id, request.getInputStream(), actor);
    }

    /** May return one-time temporary passwords, so the response is never cached. */
    @PostMapping("/commit")
    ResponseEntity<ImportResult> commit(@PathVariable UUID id, @Valid @RequestBody ImportCommand command, Actor actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(imports.commit(id, command.digest(), command.rows(), actor));
    }

    record ImportCommand(@NotNull String digest, @NotNull @Size(max = RosterImportService.MAX_ROWS) List<ImportRow> rows) {
    }
}
