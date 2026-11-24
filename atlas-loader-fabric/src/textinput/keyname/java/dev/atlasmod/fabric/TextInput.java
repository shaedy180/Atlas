package dev.atlasmod.fabric;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric API for 26.1.1 has no char event on screens yet, so typed text is
 * derived from the key name. GLFW resolves it with the active keyboard layout.
 */
final class TextInput {

    private TextInput() {}

    static String fromKey(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE) return " ";
        String name = GLFW.glfwGetKeyName(event.key(), event.scancode());
        if (name == null || name.isEmpty()) return null;
        return event.hasShiftDown() ? name.toUpperCase() : name;
    }

    static void register(Screen screen) {
        // Nothing to register, keys arrive through allowKeyPress
    }
}
