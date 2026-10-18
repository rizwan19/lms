package com.saasresearcher.learning.media.api;

import com.saasresearcher.learning.common.Actor;
import java.util.UUID;

/**
 * Grants read access to assets linked from another module (lessons, classes, branding). Media denies by
 * default and only consults registered policies, so it never imports their internals.
 */
public interface AssetAccessPolicy {

    boolean canRead(UUID assetId, Actor actor);
}
