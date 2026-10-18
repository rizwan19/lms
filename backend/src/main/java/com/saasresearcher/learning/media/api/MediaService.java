package com.saasresearcher.learning.media.api;

import com.saasresearcher.learning.common.Actor;
import java.util.UUID;

public interface MediaService {

    /** Checks read access on every call; unknown or unauthorized assets are reported as not found. */
    AuthorizedAsset authorizeRead(UUID assetId, Actor actor);
}
