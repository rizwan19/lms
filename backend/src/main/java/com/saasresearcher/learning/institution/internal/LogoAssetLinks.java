package com.saasresearcher.learning.institution.internal;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.media.api.AssetAccessPolicy;
import com.saasresearcher.learning.media.api.AssetReferences;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The current institution logo is readable by every signed-in user and is never an orphan. */
@Component
class LogoAssetLinks implements AssetAccessPolicy, AssetReferences {

    private final InstitutionService institution;

    LogoAssetLinks(InstitutionService institution) {
        this.institution = institution;
    }

    @Override
    public boolean canRead(UUID assetId, Actor actor) {
        return institution.isLogo(assetId);
    }

    @Override
    public boolean isReferenced(UUID assetId) {
        return institution.isLogo(assetId);
    }
}
