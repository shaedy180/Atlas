package dev.atlasmod.fabric;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings for Atlas.
 */
public final class AtlasKeyBindings {

    public static final String CATEGORY = "key.categories.atlas";

    public static KeyMapping openAtlas;
    public static KeyMapping toggleQuickMode;
    public static KeyMapping focusSearch;

    private AtlasKeyBindings() {}

    public static void register() {
        openAtlas = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.atlas.open",
                GLFW.GLFW_KEY_U,
                CATEGORY
        ));

        toggleQuickMode = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.atlas.quick_toggle",
                GLFW.GLFW_KEY_O,
                CATEGORY
        ));

        focusSearch = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.atlas.focus_search",
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY
        ));
    }
}
