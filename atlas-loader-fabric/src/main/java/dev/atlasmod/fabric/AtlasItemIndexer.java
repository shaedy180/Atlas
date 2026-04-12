package dev.atlasmod.fabric;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.search.SearchIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Populates the Atlas SearchIndex from the Minecraft item registry.
 * Called once per world join after the registry is fully loaded.
 */
public final class AtlasItemIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AtlasItemIndexer.class);

    private AtlasItemIndexer() {}

    /**
     * Clears and rebuilds the search index from BuiltInRegistries.ITEM.
     * Uses translated display names for search matching instead of raw
     * translation keys, so that players can search by the name they see.
     */
    public static void buildIndex(SearchIndex index) {
        index.clear();

        int count = 0;
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            Identifier id = entry.getKey().identifier();
            Item item = entry.getValue();

            EntryKey key = new EntryKey("item", id.toString());

            // Resolve the translated display name for the current language.
            // Falls back to the translation key if the client language isn't loaded yet.
            String displayName;
            try {
                ItemStack stack = new ItemStack(item);
                Component name = stack.getHoverName();
                displayName = name.getString();
            } catch (Exception e) {
                displayName = item.getDescriptionId();
            }

            // Also include the raw path (e.g. "diamond_sword") as a searchable alias
            String idStr = id.toString();
            String pathPart = idStr.contains(":") ? idStr.substring(idStr.indexOf(':') + 1) : idStr;
            String pathAlias = pathPart.replace('_', ' ');
            String searchableName = displayName + " " + pathAlias;

            // Collect tags for this item
            Set<String> tags = new HashSet<>();
            Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(item);
            holder.tags().forEach(tag -> tags.add(tag.location().toString()));

            // Collect tooltip text for tooltip search (#prefix)
            String tooltip = null;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    ItemStack stack = new ItemStack(item);
                    var lines = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player,
                            net.minecraft.world.item.TooltipFlag.NORMAL);
                    if (lines.size() > 1) {
                        StringBuilder sb = new StringBuilder();
                        // Skip first line (it's the item name)
                        for (int i = 1; i < lines.size(); i++) {
                            sb.append(lines.get(i).getString()).append(' ');
                        }
                        tooltip = sb.toString().trim();
                    }
                }
            } catch (Exception ignored) {
                // Tooltip extraction can fail for items with unusual tooltip logic
            }

            index.add(key, searchableName, tags, tooltip);
            count++;
        }

        LOGGER.info("[Atlas] Indexed {} items from registry", count);
    }
}
