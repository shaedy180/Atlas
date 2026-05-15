package dev.atlasmod.fabric;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.api.internal.AtlasMutableRegistry;
import dev.atlasmod.api.internal.AtlasRegistrationContextImpl;
import dev.atlasmod.core.invalidation.InvalidationEvent;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.registry.AtlasRegistrySnapshot;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates Atlas runtime state across server and client.
 */
public final class AtlasRuntimeController {

    private static final Logger LOGGER = LoggerFactory.getLogger("Atlas/Runtime");

    private static volatile AtlasRegistrySnapshot serverSnapshot = AtlasRegistrySnapshot.EMPTY;
    private static volatile AtlasRegistrySnapshot clientSnapshot = AtlasRegistrySnapshot.EMPTY;

    private AtlasRuntimeController() {
    }

    public static void registerLifecycleHooks() {
        ServerLifecycleEvents.SERVER_STARTED.register(AtlasRuntimeController::rebuildServerSnapshot);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                rebuildServerSnapshot(server);
                syncSnapshotToAll(server);
            }
        });
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> syncSnapshot(player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> serverSnapshot = AtlasRegistrySnapshot.EMPTY);
    }

    public static AtlasRegistrySnapshot serverSnapshot() {
        return serverSnapshot;
    }

    public static AtlasRegistrySnapshot clientSnapshot() {
        return clientSnapshot;
    }

    public static void rebuildServerSnapshot(MinecraftServer server) {
        AtlasMutableRegistry registry = new AtlasMutableRegistry();
        AtlasRegistrationContextImpl atlasContext = new AtlasRegistrationContextImpl(AtlasFabricClient.MOD_ID, registry);

        AtlasApi.attachContext(atlasContext);
        try {
            VanillaRecipeLoader.registerCategories(atlasContext);
            for (RecipeNode recipe : VanillaRecipeLoader.loadRecipes(server)) {
                registry.addRecipe(recipe);
            }
            AtlasPluginLoader.registerAll(registry);
            serverSnapshot = registry.snapshot();
            LOGGER.info("[Atlas] Built server snapshot: {} categories, {} recipes, {} owners",
                    serverSnapshot.categories().size(),
                    serverSnapshot.recipes().size(),
                    serverSnapshot.ownerModIds().size());
        } finally {
            AtlasApi.clearContext();
        }
    }

    public static void syncSnapshot(ServerPlayer player) {
        AtlasNetworking.sendSnapshot(player, serverSnapshot);
    }

    public static void syncSnapshotToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncSnapshot(player);
        }
    }

    public static void applyClientSnapshot(AtlasRegistrySnapshot snapshot) {
        clientSnapshot = snapshot;

        RecipeGraph graph = AtlasFabricClient.recipeGraph();
        graph.clear();
        for (RecipeNode recipe : snapshot.recipes()) {
            graph.addNode(recipe);
        }

        AtlasItemIndexer.buildIndex(AtlasFabricClient.searchIndex(), snapshot);
        QuickModeOverlay.onAtlasDataUpdated();
        AtlasFabricClient.invalidationEngine().fire(InvalidationEvent.RECIPE_RELOAD);
        LOGGER.info("[Atlas] Applied client snapshot: {} categories, {} recipes",
                snapshot.categories().size(),
                snapshot.recipes().size());
    }

    public static void clearClientSnapshot() {
        clientSnapshot = AtlasRegistrySnapshot.EMPTY;
        AtlasFabricClient.recipeGraph().clear();
        AtlasFabricClient.searchIndex().clear();
        ContextSnapshotBuilder.invalidateCache();
    }

    public static void requestReloadFromClient() {
        Minecraft client = Minecraft.getInstance();
        if (client.getSingleplayerServer() != null) {
            rebuildServerSnapshot(client.getSingleplayerServer());
            syncSnapshotToAll(client.getSingleplayerServer());
            return;
        }
        AtlasNetworking.requestReload();
    }
}
