package com.saasresearcher.learning.courses.service;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.courses.repository.LessonRepository;
import com.saasresearcher.learning.media.api.AssetAccessPolicy;
import com.saasresearcher.learning.media.api.AssetReferences;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A course's author may read files used by any edition of it; used files are never orphans. */
@Component
public class CourseAssetLinks implements AssetAccessPolicy, AssetReferences {

    private final LessonRepository lessons;

    CourseAssetLinks(LessonRepository lessons) {
        this.lessons = lessons;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(UUID assetId, Actor actor) {
        return lessons.authorUsesAsset(actor.userId(), assetId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isReferenced(UUID assetId) {
        return !lessons.versionsUsingAsset(assetId).isEmpty();
    }
}
