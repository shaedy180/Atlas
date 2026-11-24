package dev.atlasmod.fabric;

import dev.atlasmod.api.plugin.AtlasPlugin;
import dev.atlasmod.client.AtlasClient;
import dev.atlasmod.client.AtlasCommands;
import dev.atlasmod.client.AtlasKeyBindings;
import dev.atlasmod.client.QuickModeOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric entry point. Wires Fabric events to the shared Atlas client.
 * Third-party plugins are listed under the "atlas" entrypoint key.
 */
public final class AtlasFabricClient implements ClientModInitializer {

    private static final String ENTRYPOINT_KEY = "atlas";

    @Override
    public void onInitializeClient() {
        AtlasClient.init();

        KeyMapping.Category category = KeyMapping.Category.register(AtlasKeyBindings.CATEGORY_ID);
        AtlasKeyBindings.create(category).forEach(KeyMappingHelper::registerKeyMapping);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(AtlasCommands.<FabricClientCommandSource>build(FabricClientCommandSource::sendFeedback)));

        AtlasClient.loadPlugins(discoverPlugins());

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenEvents.afterExtract(screen).register((scr, graphics, mouseX, mouseY, tickDelta) ->
                    QuickModeOverlay.render(scr, graphics, mouseX, mouseY));
            ScreenMouseEvents.allowMouseClick(screen).register((scr, event) -> !QuickModeOverlay.handleClick(scr, event));
            ScreenMouseEvents.allowMouseRelease(screen).register((scr, event) -> {
                QuickModeOverlay.handleRelease(event);
                return true;
            });
            ScreenMouseEvents.allowMouseScroll(screen).register((scr, mouseX, mouseY, scrollX, scrollY) ->
                    !QuickModeOverlay.handleScroll(scr, mouseX, mouseY, scrollY));
            ScreenKeyboardEvents.allowKeyPress(screen).register((scr, event) -> {
                String typed = TextInput.fromKey(event);
                if (typed != null && QuickModeOverlay.handleChar(scr, typed)) return false;
                return !QuickModeOverlay.handleKeyPress(scr, event);
            });
            TextInput.register(screen);
        });

        // Recipes are read on the first tick after joining, when the integrated server is ready
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> AtlasClient.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AtlasClient.onLeave());
        ClientTickEvents.END_CLIENT_TICK.register(AtlasClient::onTick);

        AtlasClient.LOGGER.info("[Atlas] Initialization complete. Waiting for world join.");
    }

    private static List<AtlasClient.PluginEntry> discoverPlugins() {
        List<AtlasClient.PluginEntry> found = new ArrayList<>();
        for (var container : FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT_KEY, AtlasPlugin.class)) {
            String modId = container.getProvider().getMetadata().getId();
            try {
                found.add(new AtlasClient.PluginEntry(modId, container.getEntrypoint()));
            } catch (Throwable t) {
                AtlasClient.LOGGER.error("[Atlas] Could not create plugin from mod '{}'", modId, t);
            }
        }
        return found;
    }
}
