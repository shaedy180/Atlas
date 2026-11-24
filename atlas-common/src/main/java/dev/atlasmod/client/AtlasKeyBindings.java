package dev.atlasmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Key bindings for Atlas. Created here, registered by the loader.
 */
public final class AtlasKeyBindings {

    public static final Identifier CATEGORY_ID = Identifier.fromNamespaceAndPath("atlas", "atlas");

    public static KeyMapping openAtlas;
    public static KeyMapping toggleQuickMode;
    public static KeyMapping focusSearch;

    private AtlasKeyBindings() {}

    /** Creates the mappings in the given category and returns them for registration. */
    public static List<KeyMapping> create(KeyMapping.Category category) {
        openAtlas = new KeyMapping("key.atlas.open", InputConstants.KEY_U, category);
        toggleQuickMode = new KeyMapping("key.atlas.quick_toggle", InputConstants.KEY_O, category);
        focusSearch = new KeyMapping("key.atlas.focus_search", InputConstants.UNKNOWN.getValue(), category);
        return List.of(openAtlas, toggleQuickMode, focusSearch);
    }
}
