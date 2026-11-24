package dev.atlasmod.neoforge;

import dev.atlasmod.api.plugin.AtlasEntrypoint;
import dev.atlasmod.api.plugin.AtlasPlugin;
import dev.atlasmod.client.AtlasClient;
import dev.atlasmod.client.AtlasCommands;
import dev.atlasmod.client.AtlasKeyBindings;
import dev.atlasmod.client.QuickModeOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge entry point. Wires NeoForge events to the shared Atlas client.
 * Third-party plugins are classes annotated with {@link AtlasEntrypoint}.
 */
@Mod(value = AtlasClient.MOD_ID, dist = Dist.CLIENT)
public final class AtlasNeoForge {

    public AtlasNeoForge(IEventBus modBus) {
        AtlasClient.init();

        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            KeyMapping.Category category = new KeyMapping.Category(AtlasKeyBindings.CATEGORY_ID);
            event.registerCategory(category);
            AtlasKeyBindings.create(category).forEach(event::register);
        });
        modBus.addListener(FMLClientSetupEvent.class, event ->
                event.enqueueWork(() -> AtlasClient.loadPlugins(discoverPlugins())));

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher().register(
                AtlasCommands.<CommandSourceStack>build((source, line) -> source.sendSuccess(() -> line, false))));

        // Recipes are read on the first tick after joining, when the integrated server is ready
        bus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> AtlasClient.onJoin());
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            if (event.getPlayer() != null) AtlasClient.onLeave();
        });
        bus.addListener(ClientTickEvent.Post.class, event -> AtlasClient.onTick(Minecraft.getInstance()));

        bus.addListener(ScreenEvent.Render.Post.class, event ->
                QuickModeOverlay.render(event.getScreen(), event.getGuiGraphics(), event.getMouseX(), event.getMouseY()));
        bus.addListener(ScreenEvent.MouseButtonPressed.Pre.class, event -> {
            if (QuickModeOverlay.handleClick(event.getScreen(), event.getMouseButtonEvent())) event.setCanceled(true);
        });
        bus.addListener(ScreenEvent.MouseButtonReleased.Pre.class, event ->
                QuickModeOverlay.handleRelease(event.getMouseButtonEvent()));
        bus.addListener(ScreenEvent.MouseScrolled.Pre.class, event -> {
            if (QuickModeOverlay.handleScroll(event.getScreen(), event.getMouseX(), event.getMouseY(),
                    event.getScrollDeltaY())) event.setCanceled(true);
        });
        bus.addListener(ScreenEvent.KeyPressed.Pre.class, event -> {
            if (QuickModeOverlay.handleKeyPress(event.getScreen(), event.getKeyEvent())) event.setCanceled(true);
        });
        bus.addListener(ScreenEvent.CharacterTyped.Pre.class, event -> {
            String text = event.getCharacterEvent().codepointAsString();
            if (QuickModeOverlay.handleChar(event.getScreen(), text)) event.setCanceled(true);
        });

        AtlasClient.LOGGER.info("[Atlas] Initialization complete. Waiting for world join.");
    }

    private static List<AtlasClient.PluginEntry> discoverPlugins() {
        List<AtlasClient.PluginEntry> found = new ArrayList<>();
        for (var scan : ModList.get().getAllScanData()) {
            String modId = scan.getIModInfoData().isEmpty() || scan.getIModInfoData().getFirst().getMods().isEmpty()
                    ? "unknown" : scan.getIModInfoData().getFirst().getMods().getFirst().getModId();
            scan.getAnnotatedBy(AtlasEntrypoint.class, ElementType.TYPE).forEach(annotation -> {
                String className = annotation.clazz().getClassName();
                try {
                    Class<?> type = Class.forName(className, true, AtlasNeoForge.class.getClassLoader());
                    Object instance = type.getDeclaredConstructor().newInstance();
                    if (instance instanceof AtlasPlugin plugin) {
                        found.add(new AtlasClient.PluginEntry(modId, plugin));
                    } else {
                        AtlasClient.LOGGER.warn("[Atlas] {} is annotated with @AtlasEntrypoint but is no AtlasPlugin", className);
                    }
                } catch (Throwable t) {
                    AtlasClient.LOGGER.error("[Atlas] Could not create plugin {} from mod '{}'", className, modId, t);
                }
            });
        }
        return found;
    }
}
