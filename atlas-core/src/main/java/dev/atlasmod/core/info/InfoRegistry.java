package dev.atlasmod.core.info;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.TagResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds every info page. Pages are replaced by id, so reloading a resource pack
 * does not create duplicates.
 */
public final class InfoRegistry {

    private final Map<String, InfoPage> pages = new LinkedHashMap<>();

    public synchronized void register(InfoPage page) {
        pages.put(page.id(), page);
    }

    /**
     * Removes all pages from one source, used before reloading that source.
     * Also matches sub sources written as "source:detail".
     */
    public synchronized void clearSource(String source) {
        pages.values().removeIf(p -> p.source().equals(source) || p.source().startsWith(source + ":"));
    }

    public synchronized Collection<InfoPage> all() {
        return List.copyOf(pages.values());
    }

    /** Pages for the entry, matched by item id or by one of its tags. */
    public synchronized List<InfoPage> pagesFor(EntryKey entry, TagResolver tags) {
        List<InfoPage> result = new ArrayList<>();
        var itemTags = tags.tagsOf(entry.id());
        for (InfoPage page : pages.values()) {
            for (String target : page.targets()) {
                boolean hit = target.startsWith("#")
                        ? itemTags.contains(target.substring(1))
                        : target.equals(entry.id());
                if (hit) {
                    result.add(page);
                    break;
                }
            }
        }
        return result;
    }

    public synchronized int size() {
        return pages.size();
    }
}
