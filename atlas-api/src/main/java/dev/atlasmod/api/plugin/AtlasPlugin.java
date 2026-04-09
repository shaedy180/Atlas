package dev.atlasmod.api.plugin;

/**
 * Marker interface for Atlas plugins provided by other mods.
 * Mods implement this and register it via their mod initializer or
 * a datapack-driven entrypoint.
 */
public interface AtlasPlugin {

    /**
     * Called when Atlas is ready to accept registrations.
     * Register your categories, recipes, info pages, etc. here.
     */
    void onAtlasReady();
}
