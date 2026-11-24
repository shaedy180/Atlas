package dev.atlasmod.client;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.search.SearchIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
     * Items are found by their shown name (in the game language), their id and
     * their translation key. Tooltip lines feed the "#text" search.
     */
    public static void buildIndex(SearchIndex index) {
        index.clear();
        var mc = Minecraft.getInstance();
        Item.TooltipContext context = mc.level != null ? Item.TooltipContext.of(mc.level) : Item.TooltipContext.EMPTY;

        int count = 0;
        for (var holder : BuiltInRegistries.ITEM.listElements().toList()) {
            String id = holder.key().identifier().toString();
            var item = holder.value();
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) continue; // air

            String shown = stack.getHoverName().getString();
            String searchable = shown + " " + id.substring(id.indexOf(':') + 1).replace('_', ' ')
                    + " " + item.getDescriptionId();

            Set<String> tags = new HashSet<>();
            holder.tags().forEach(tag -> tags.add(tag.location().toString()));

            index.add(new EntryKey("item", id), searchable, tags, tooltip(stack, context, mc));
            count++;
        }

        LOGGER.info("[Atlas] Indexed {} items from registry", count);
    }

    private static String tooltip(ItemStack stack, Item.TooltipContext context, Minecraft mc) {
        try {
            StringBuilder text = new StringBuilder();
            for (Component line : stack.getTooltipLines(context, mc.player, TooltipFlag.NORMAL)) {
                text.append(line.getString()).append('\n');
            }
            return text.toString();
        } catch (RuntimeException e) {
            // Some modded items expect more context than a registry scan has
            return stack.getHoverName().getString();
        }
    }
}
