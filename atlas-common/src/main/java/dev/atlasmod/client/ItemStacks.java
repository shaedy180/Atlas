package dev.atlasmod.client;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between Atlas keys and item stacks, and finds display names.
 */
public final class ItemStacks {

    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    private ItemStacks() {}

    public static void clearCache() {
        CACHE.clear();
    }

    public static ItemStack of(EntryKey entry) {
        if (!"item".equals(entry.type())) return ItemStack.EMPTY;
        return of(entry.id());
    }

    public static ItemStack of(String itemId) {
        ItemStack cached = CACHE.get(itemId);
        if (cached != null) return cached;
        ItemStack stack = ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(itemId);
        if (id != null) {
            var holder = BuiltInRegistries.ITEM.get(id);
            if (holder.isPresent()) stack = new ItemStack(holder.get().value());
        }
        CACHE.put(itemId, stack);
        return stack;
    }

    /** Tag ingredients cycle through their members once per second, like a recipe book. */
    public static ItemStack of(IngredientKey ingredient) {
        if (ingredient.isEmpty()) return ItemStack.EMPTY;
        if (!ingredient.tagBased()) return of(ingredient.id());
        List<String> members = AtlasClient.tags().members(ingredient.id());
        if (members.isEmpty()) return ItemStack.EMPTY;
        int index = (int) ((System.currentTimeMillis() / 1000) % members.size());
        return of(members.get(index));
    }

    public static EntryKey entryOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var key = stack.typeHolder().unwrapKey().orElse(null);
        return key == null ? null : new EntryKey("item", key.identifier().toString());
    }

    /**
     * Display name for an item, block or any other id. Falls back to a prettified
     * path. Tags ("#id") with a single item use that item's name.
     */
    public static String name(String id) {
        if (id.startsWith("#")) {
            List<String> members = AtlasClient.tags().members(id.substring(1));
            return members.size() == 1 ? name(members.getFirst()) : "any " + tagName(id.substring(1));
        }
        ItemStack stack = of(id);
        if (!stack.isEmpty()) return stack.getHoverName().getString();
        return pretty(id);
    }

    /** "c:ingots/iron" becomes "Ingots Iron", "minecraft:iron_tool_materials" becomes "Iron Tool Materials". */
    public static String tagName(String tagId) {
        String path = tagId.contains(":") ? tagId.substring(tagId.indexOf(':') + 1) : tagId;
        return pretty(path.replace('/', '_'));
    }

    /** "minecraft:the_nether" becomes "The Nether". */
    public static String pretty(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        path = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? id : out.toString();
    }
}
