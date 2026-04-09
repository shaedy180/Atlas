package dev.atlasmod.core.recipe;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;
import dev.atlasmod.core.unlock.UnlockCondition;

import java.util.List;
import java.util.Optional;

/**
 * Represents a single recipe node in the Atlas knowledge graph.
 * This is a semantic data object — Atlas renders the UI from this, not from
 * mod-provided screens.
 */
public final class RecipeNode {

    private final String id;
    private final String categoryId;
    private final List<IngredientKey> inputs;
    private final List<EntryKey> outputs;
    private final StationKey station;
    private final int processingTime;
    private final int energyCost;
    private final UnlockCondition unlockCondition;
    private final List<AcquisitionSource> sources;

    private RecipeNode(Builder builder) {
        this.id = builder.id;
        this.categoryId = builder.categoryId;
        this.inputs = List.copyOf(builder.inputs);
        this.outputs = List.copyOf(builder.outputs);
        this.station = builder.station;
        this.processingTime = builder.processingTime;
        this.energyCost = builder.energyCost;
        this.unlockCondition = builder.unlockCondition;
        this.sources = builder.sources == null ? List.of() : List.copyOf(builder.sources);
    }

    public String id() { return id; }
    public String categoryId() { return categoryId; }
    public List<IngredientKey> inputs() { return inputs; }
    public List<EntryKey> outputs() { return outputs; }
    public Optional<StationKey> station() { return Optional.ofNullable(station); }
    public int processingTime() { return processingTime; }
    public int energyCost() { return energyCost; }
    public Optional<UnlockCondition> unlockCondition() { return Optional.ofNullable(unlockCondition); }
    public List<AcquisitionSource> sources() { return sources; }

    public static Builder builder(String id, String categoryId) {
        return new Builder(id, categoryId);
    }

    public static final class Builder {
        private final String id;
        private final String categoryId;
        private List<IngredientKey> inputs = List.of();
        private List<EntryKey> outputs = List.of();
        private StationKey station;
        private int processingTime;
        private int energyCost;
        private UnlockCondition unlockCondition;
        private List<AcquisitionSource> sources;

        private Builder(String id, String categoryId) {
            this.id = id;
            this.categoryId = categoryId;
        }

        public Builder inputs(List<IngredientKey> inputs) { this.inputs = inputs; return this; }
        public Builder outputs(List<EntryKey> outputs) { this.outputs = outputs; return this; }
        public Builder station(StationKey station) { this.station = station; return this; }
        public Builder processingTime(int ticks) { this.processingTime = ticks; return this; }
        public Builder energyCost(int energy) { this.energyCost = energy; return this; }
        public Builder unlockCondition(UnlockCondition cond) { this.unlockCondition = cond; return this; }
        public Builder sources(List<AcquisitionSource> sources) { this.sources = sources; return this; }

        public RecipeNode build() {
            return new RecipeNode(this);
        }
    }
}
