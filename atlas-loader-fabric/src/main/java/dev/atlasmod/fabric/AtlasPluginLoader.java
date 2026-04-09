package dev.atlasmod.fabric;

import dev.atlasmod.api.plugin.AtlasPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Discovers and invokes AtlasPlugin entrypoints registered by other mods.
 *
 * Mods register their plugin in fabric.mod.json under the "atlas" entrypoint key.
 * Each plugin's onAtlasReady() is called after Atlas has finished its own initialization,
 * so the API is safe to use. Exceptions from individual plugins are caught and logged
 * without affecting other plugins or Atlas itself.
 */
public final class AtlasPluginLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(AtlasPluginLoader.class);
    private static final String ENTRYPOINT_KEY = "atlas";

    private AtlasPluginLoader() {}

    /**
     * Discovers all mods providing an "atlas" entrypoint and calls onAtlasReady() on each.
     * Returns the number of plugins successfully invoked.
     */
    public static int loadAll() {
        List<EntrypointContainer<AtlasPlugin>> containers =
                FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT_KEY, AtlasPlugin.class);

        if (containers.isEmpty()) {
            LOGGER.debug("[Atlas] No third-party Atlas plugins found");
            return 0;
        }

        int loaded = 0;
        for (EntrypointContainer<AtlasPlugin> container : containers) {
            String modId = container.getProvider().getMetadata().getId();
            try {
                AtlasPlugin plugin = container.getEntrypoint();
                plugin.onAtlasReady();
                loaded++;
                LOGGER.info("[Atlas] Loaded plugin from mod '{}'", modId);
            } catch (Throwable t) {
                // Catch everything including LinkageError so one broken plugin
                // does not take down the rest of the mod
                LOGGER.error("[Atlas] Plugin from mod '{}' threw during onAtlasReady(), skipping", modId, t);
            }
        }

        LOGGER.info("[Atlas] {} of {} third-party plugins loaded", loaded, containers.size());
        return loaded;
    }
}
