package dev.atlasmod.api.plugin;

import dev.atlasmod.api.AtlasRegistrationContext;

/**
 * Atlas integrations register data through a scoped context.
 */
public interface AtlasPlugin {

    /**
     * Called during Atlas registry construction.
     */
    void register(AtlasRegistrationContext context);
}
