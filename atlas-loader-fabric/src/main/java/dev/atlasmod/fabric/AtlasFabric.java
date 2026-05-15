package dev.atlasmod.fabric;

import net.fabricmc.api.ModInitializer;

/**
 * Common Atlas entrypoint used on both dedicated server and integrated server.
 */
public final class AtlasFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        AtlasNetworking.register();
        AtlasRuntimeController.registerLifecycleHooks();
    }
}
