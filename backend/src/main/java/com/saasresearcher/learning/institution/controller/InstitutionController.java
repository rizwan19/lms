package com.saasresearcher.learning.institution.controller;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.Timezones;
import com.saasresearcher.learning.institution.api.InstitutionProfile;
import com.saasresearcher.learning.institution.service.InstitutionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/institution")
public class InstitutionController {

    private final InstitutionService institution;

    InstitutionController(InstitutionService institution) {
        this.institution = institution;
    }

    /** Public: the login page shows the institution name. */
    @GetMapping
    InstitutionView get() {
        return InstitutionView.of(institution.get());
    }

    @PutMapping
    InstitutionView update(@Valid @RequestBody UpdateInstitution command, Actor actor) {
        return InstitutionView.of(institution.update(command.name(), Timezones.parse("timezone", command.timezone()), command.logoAssetId(),
                command.certificateVerification(), command.verificationShowsStudent(), command.expectedRevision(), actor));
    }

    record UpdateInstitution(@NotBlank @Size(max = 200) String name, @NotBlank String timezone, UUID logoAssetId, boolean certificateVerification,
            boolean verificationShowsStudent, long expectedRevision) {
    }

    record InstitutionView(String name, String timezone, UUID logoAssetId, boolean certificateVerification, boolean verificationShowsStudent,
            long revision) {

        static InstitutionView of(InstitutionProfile profile) {
            return new InstitutionView(profile.name(), profile.timezone().getId(), profile.logoAssetId(), profile.certificateVerification(),
                    profile.verificationShowsStudent(), profile.revision());
        }
    }
}
