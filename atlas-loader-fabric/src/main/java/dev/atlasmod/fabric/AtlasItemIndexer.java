package dev.atlasmod.fabric;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.recipe.AcquisitionSource;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.registry.AtlasRegistrySnapshot;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    public static void buildIndex(SearchIndex index, AtlasRegistrySnapshot snapshot) {
        index.clear();

        Map<EntryKey, Set<String>> sourceTypesByEntry = new HashMap<>();
        Map<EntryKey, Set<String>> stationsByEntry = new HashMap<>();
        Map<EntryKey, List<String>> aliasesByEntry = new HashMap<>();
        Set<EntryKey> renewableEntries = new HashSet<>();

        for (RecipeNode recipe : snapshot.recipes()) {
            String sourceType = sourceTypeForRecipe(recipe.categoryId());
            for (EntryKey output : recipe.outputs()) {
                if (sourceType != null) {
                    sourceTypesByEntry.computeIfAbsent(output, ignored -> new HashSet<>()).add(sourceType);
                }
                recipe.station().ifPresent(station ->
                        stationsByEntry.computeIfAbsent(output, ignored -> new HashSet<>()).add(station.id()));
                if (!recipe.searchAliases().isEmpty()) {
                    aliasesByEntry.computeIfAbsent(output, ignored -> new ArrayList<>()).addAll(recipe.searchAliases());
                }
            }
        }

        snapshot.sourcesByEntry().forEach((entry, sources) -> {
            for (AcquisitionSource source : sources) {
                sourceTypesByEntry.computeIfAbsent(entry, ignored -> new HashSet<>())
                        .add(source.type().name().toLowerCase());
                if (source.renewable()) {
                    renewableEntries.add(entry);
                }
            }
        });

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
            StringBuilder searchableName = new StringBuilder(displayName).append(' ').append(pathAlias);

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

            List<String> extraAliases = aliasesByEntry.getOrDefault(key, List.of());
            for (String alias : extraAliases) {
                searchableName.append(' ').append(alias);
            }

            index.add(
                    key,
                    searchableName.toString(),
                    tags,
                    tooltip,
                    sourceTypesByEntry.getOrDefault(key, Set.of()),
                    stationsByEntry.getOrDefault(key, Set.of()),
                    renewableEntries.contains(key)
            );
            count++;
        }

        LOGGER.info("[Atlas] Indexed {} items from registry", count);
    }

    private static String sourceTypeForRecipe(String categoryId) {
        return switch (categoryId) {
            case "minecraft:crafting" -> "crafting";
            case "minecraft:smelting" -> "smelting";
            case "minecraft:blasting" -> "blasting";
            case "minecraft:smoking" -> "smoking";
            case "minecraft:campfire" -> "campfire";
            case "minecraft:stonecutting" -> "stonecutting";
            case "minecraft:smithing" -> "smithing";
            default -> null;
        };
    }
}
