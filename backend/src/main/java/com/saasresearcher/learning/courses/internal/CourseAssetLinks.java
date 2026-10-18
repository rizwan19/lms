package com.saasresearcher.learning.courses.internal;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.media.api.AssetAccessPolicy;
import com.saasresearcher.learning.media.api.AssetReferences;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Course teachers may read files used by any edition of their courses; used files are never orphans. */
@Component
class CourseAssetLinks implements AssetAccessPolicy, AssetReferences {

    private final CourseContentRepository content;

    CourseAssetLinks(CourseContentRepository content) {
        this.content = content;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID assetId, Actor actor) {
        return content.teacherUsesAsset(actor.userId(), assetId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReferenced(UUID assetId) {
        return !content.versionsUsingAsset(assetId).isEmpty();
    }
}
