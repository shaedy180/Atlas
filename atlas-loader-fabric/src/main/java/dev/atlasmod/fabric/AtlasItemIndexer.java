package dev.atlasmod.fabric;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.search.SearchIndex;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
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
     */
    public static void buildIndex(SearchIndex index) {
        index.clear();

        int count = 0;
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            Identifier id = entry.getKey().identifier();
            Item item = entry.getValue();

            EntryKey key = new EntryKey("item", id.toString());
            String displayName = item.getDescriptionId();

            // Collect tags for this item
            Set<String> tags = new HashSet<>();
            Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(item);
            holder.tags().forEach(tag -> tags.add(tag.location().toString()));

            index.add(key, displayName, tags, null);
            count++;
        }

        LOGGER.info("[Atlas] Indexed {} items from registry", count);
    }
}
