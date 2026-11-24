package dev.atlasmod.core.info;

import java.util.List;
import java.util.Objects;

/**
 * A short page of text attached to one or more items, for example pack specific
 * tips ("Iron is rare in this pack, use the sieve").
 *
 * @param targets item ids, or tags written as {@code #namespace:path}
 * @param source  where the page came from, e.g. a resource pack or a mod id
 */
public record InfoPage(String id, String title, List<String> lines, List<String> targets, String source) {

    public InfoPage {
        Objects.requireNonNull(id, "id must not be null");
        title = title == null ? "" : title;
        lines = List.copyOf(lines);
        targets = List.copyOf(targets);
        source = source == null ? "" : source;
    }
}
