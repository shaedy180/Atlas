package dev.atlasmod.api.registration;

import dev.atlasmod.core.info.InfoPage;
import dev.atlasmod.core.info.InfoRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for info pages.
 * <pre>{@code
 * AtlasApi.info("mymod:ore_tips")
 *     .title("Finding tin")
 *     .line("Tin spawns below Y 40 in mountain biomes.")
 *     .forItems("mymod:raw_tin", "#c:ores/tin")
 *     .register();
 * }</pre>
 */
public final class InfoRegistration {

    private final InfoRegistry registry;
    private final String id;
    private String title = "";
    private String source = "api";
    private final List<String> lines = new ArrayList<>();
    private final List<String> targets = new ArrayList<>();

    public InfoRegistration(InfoRegistry registry, String id) {
        this.registry = Objects.requireNonNull(registry);
        this.id = Objects.requireNonNull(id);
    }

    public InfoRegistration title(String title) {
        this.title = title;
        return this;
    }

    public InfoRegistration line(String line) {
        lines.add(line);
        return this;
    }

    public InfoRegistration lines(String... lines) {
        this.lines.addAll(List.of(lines));
        return this;
    }

    /** Item ids, or tags written as {@code #namespace:path}. */
    public InfoRegistration forItems(String... targets) {
        this.targets.addAll(List.of(targets));
        return this;
    }

    /** Shown under the page so players know who wrote it, e.g. your mod name. */
    public InfoRegistration source(String source) {
        this.source = source;
        return this;
    }

    public void register() {
        if (targets.isEmpty()) throw new IllegalStateException("Info page " + id + " has no target items");
        registry.register(new InfoPage(id, title, lines, targets, source));
    }
}
