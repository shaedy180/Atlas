package dev.atlasmod.core.recipe;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;
import dev.atlasmod.core.unlock.UnlockCondition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a single recipe node in the Atlas knowledge graph.
 * This is a semantic data object. Atlas renders the UI from this, not from
 * mod-provided screens.
 */
public final class RecipeNode {

    private final String id;
    private final String categoryId;
    private final List<IngredientKey> inputs;
    private final List<EntryKey> outputs;
    private final int outputCount;
    private final StationKey station;
    private final int processingTime;
    private final int energyCost;
    private final int gridWidth;
    private final int gridHeight;
    private final UnlockCondition unlockCondition;
    private final List<AcquisitionSource> sources;

    private RecipeNode(Builder builder) {
        this.id = builder.id;
        this.categoryId = builder.categoryId;
        this.inputs = List.copyOf(builder.inputs);
        this.outputs = List.copyOf(builder.outputs);
        this.outputCount = Math.max(1, builder.outputCount);
        this.station = builder.station;
        this.processingTime = builder.processingTime;
        this.energyCost = builder.energyCost;
        this.gridWidth = builder.gridWidth;
        this.gridHeight = builder.gridHeight;
        this.unlockCondition = builder.unlockCondition;
        this.sources = builder.sources == null ? List.of() : List.copyOf(builder.sources);
    }

    public String id() { return id; }
    public String categoryId() { return categoryId; }
    public List<IngredientKey> inputs() { return inputs; }
    public List<EntryKey> outputs() { return outputs; }
    /** How many of the first output one craft produces. */
    public int outputCount() { return outputCount; }
    public Optional<StationKey> station() { return Optional.ofNullable(station); }
    public int processingTime() { return processingTime; }
    public int energyCost() { return energyCost; }
    /** Grid width for shaped recipes (0 if not shaped). */
    public int gridWidth() { return gridWidth; }
    /** Grid height for shaped recipes (0 if not shaped). */
    public int gridHeight() { return gridHeight; }
    public Optional<UnlockCondition> unlockCondition() { return Optional.ofNullable(unlockCondition); }
    public List<AcquisitionSource> sources() { return sources; }

    /** Inputs grouped by ingredient with their per-craft amount. Empty grid slots are skipped. */
    public Map<IngredientKey, Integer> ingredientCounts() {
        Map<IngredientKey, Integer> counts = new LinkedHashMap<>();
        for (IngredientKey input : inputs) {
            if (!input.isEmpty()) counts.merge(input, 1, Integer::sum);
        }
        return counts;
    }

    /** Whether this recipe produces the given entry. */
    public boolean produces(EntryKey entry) {
        return outputs.contains(entry);
    }

    public static Builder builder(String id, String categoryId) {
        return new Builder(id, categoryId);
    }

    public static final class Builder {
        private final String id;
        private final String categoryId;
        private List<IngredientKey> inputs = List.of();
        private List<EntryKey> outputs = List.of();
        private int outputCount = 1;
        private StationKey station;
        private int processingTime;
        private int energyCost;
        private int gridWidth;
        private int gridHeight;
        private UnlockCondition unlockCondition;
        private List<AcquisitionSource> sources;

        private Builder(String id, String categoryId) {
            this.id = Objects.requireNonNull(id, "recipe id must not be null");
            this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
        }

        public Builder inputs(List<IngredientKey> inputs) { this.inputs = inputs; return this; }
        public Builder outputs(List<EntryKey> outputs) { this.outputs = outputs; return this; }
        public Builder outputCount(int count) { this.outputCount = count; return this; }
        public Builder station(StationKey station) { this.station = station; return this; }
        public Builder processingTime(int ticks) { this.processingTime = ticks; return this; }
        public Builder energyCost(int energy) { this.energyCost = energy; return this; }
        public Builder grid(int width, int height) { this.gridWidth = width; this.gridHeight = height; return this; }
        public Builder unlockCondition(UnlockCondition cond) { this.unlockCondition = cond; return this; }
        public Builder sources(List<AcquisitionSource> sources) { this.sources = sources; return this; }

        public RecipeNode build() {
            return new RecipeNode(this);
        }
    }
}
