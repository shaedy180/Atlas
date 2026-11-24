package dev.atlasmod.client;

import dev.atlasmod.core.entry.TagResolver;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Item tags read from the live registry. Built once per world load because tags
 * can change with datapacks.
 */
public final class RegistryTags implements TagResolver {

    private final Map<String, List<String>> members = new HashMap<>();
    private final Map<String, Set<String>> tagsOf = new HashMap<>();

    public static RegistryTags snapshot() {
        RegistryTags tags = new RegistryTags();
        for (var holder : BuiltInRegistries.ITEM.listElements().toList()) {
            String itemId = holder.key().identifier().toString();
            holder.tags().forEach(tag -> {
                String tagId = tag.location().toString();
                tags.members.computeIfAbsent(tagId, k -> new ArrayList<>()).add(itemId);
                tags.tagsOf.computeIfAbsent(itemId, k -> new HashSet<>()).add(tagId);
            });
        }
        return tags;
    }

    @Override
    public List<String> members(String tagId) {
        return members.getOrDefault(tagId, List.of());
    }

    @Override
    public Set<String> tagsOf(String itemId) {
        return tagsOf.getOrDefault(itemId, Set.of());
    }

    public int tagCount() {
        return members.size();
    }
}
