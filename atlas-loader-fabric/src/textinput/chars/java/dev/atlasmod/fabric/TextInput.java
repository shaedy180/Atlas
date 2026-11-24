package dev.atlasmod.fabric;

import dev.atlasmod.client.QuickModeOverlay;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/** Typed text comes from Fabric's char event, so every keyboard layout works. */
final class TextInput {

    private TextInput() {}

    static String fromKey(KeyEvent event) {
        return null;
    }

    static void register(Screen screen) {
        ScreenKeyboardEvents.allowCharType(screen).register((scr, event) ->
                !QuickModeOverlay.handleChar(scr, event.codepointAsString()));
    }
}
