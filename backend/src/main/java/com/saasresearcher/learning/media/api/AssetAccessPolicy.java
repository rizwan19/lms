package com.saasresearcher.learning.media.api;

import com.saasresearcher.learning.common.Actor;
import java.util.UUID;

public interface AssetAccessPolicy {

    boolean canRead(UUID assetId, Actor actor);
}
