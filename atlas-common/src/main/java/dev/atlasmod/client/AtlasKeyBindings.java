package dev.atlasmod.fabric;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings for Atlas.
 */
public final class AtlasKeyBindings {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("atlas", "atlas"));

    public static KeyMapping openAtlas;
    public static KeyMapping toggleQuickMode;
    public static KeyMapping focusSearch;

    private AtlasKeyBindings() {}

    public static void register() {
        openAtlas = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.atlas.open",
                GLFW.GLFW_KEY_U,
                CATEGORY
        ));

        toggleQuickMode = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.atlas.quick_toggle",
                GLFW.GLFW_KEY_O,
                CATEGORY
        ));

        focusSearch = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.atlas.focus_search",
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY
        ));
    }
}
