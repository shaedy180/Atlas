package dev.atlasmod.api.registration;

import dev.atlasmod.api.AtlasApi;
import dev.atlasmod.core.category.RecipeCategory;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;
import dev.atlasmod.core.recipe.AcquisitionSource;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.unlock.UnlockCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fluent builder for registering recipes with Atlas.
 * Recipes added here survive world reloads. When no station is given, the first
 * station of the category is used.
 */
public final class RecipeRegistration {

    private final RecipeGraph graph;
    private final String categoryId;
    private String id;
    private final List<IngredientKey> inputs = new ArrayList<>();
    private final List<EntryKey> outputs = new ArrayList<>();
    private final List<AcquisitionSource> sources = new ArrayList<>();
    private int outputCount = 1;
    private StationKey station;
    private int time;
    private int energy;
    private int gridWidth;
    private int gridHeight;
    private UnlockCondition unlockCondition;

    public RecipeRegistration(RecipeGraph graph, String categoryId) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
    }

    /** Stable id, e.g. "mymod:alloy/bronze". Without one a random id is used. */
    public RecipeRegistration id(String id) {
        this.id = id;
        return this;
    }

    /** Inputs in slot order. Repeat an ingredient to require it several times. */
    public RecipeRegistration display(IngredientKey... inputs) {
        this.inputs.addAll(List.of(inputs));
        return this;
    }

    /** Adds the same input {@code count} times. */
    public RecipeRegistration input(IngredientKey input, int count) {
        for (int i = 0; i < count; i++) inputs.add(input);
        return this;
    }

    public RecipeRegistration output(EntryKey... outputs) {
        this.outputs.addAll(List.of(outputs));
        return this;
    }

    /** Main output and how many one craft makes. Used for tree and material totals. */
    public RecipeRegistration output(EntryKey output, int count) {
        this.outputs.add(output);
        this.outputCount = count;
        return this;
    }

    public RecipeRegistration station(StationKey station) {
        this.station = station;
        return this;
    }

    public RecipeRegistration time(int ticks) {
        this.time = ticks;
        return this;
    }

    public RecipeRegistration energy(int energy) {
        this.energy = energy;
        return this;
    }

    /** Shaped grid size. Inputs are then read row by row with {@code IngredientKey.EMPTY} as gaps. */
    public RecipeRegistration grid(int width, int height) {
        this.gridWidth = width;
        this.gridHeight = height;
        return this;
    }

    /** Replaces the unlock condition. */
    public RecipeRegistration unlock(UnlockCondition condition) {
        this.unlockCondition = condition;
        return this;
    }

    /** Adds a condition on top of the existing ones. */
    public RecipeRegistration require(UnlockCondition condition) {
        this.unlockCondition = UnlockCondition.and(unlockCondition, condition);
        return this;
    }

    public RecipeRegistration source(AcquisitionSource source) {
        this.sources.add(source);
        return this;
    }

    public void register() {
        String nodeId = this.id != null ? this.id : categoryId + "/" + UUID.randomUUID();
        StationKey resolvedStation = station;
        if (resolvedStation == null && AtlasApi.isReady()) {
            resolvedStation = AtlasApi.get().categoryById(categoryId)
                    .map(RecipeCategory::stations)
                    .filter(list -> !list.isEmpty())
                    .map(list -> new StationKey(list.getFirst()))
                    .orElse(null);
        }
        RecipeNode node = RecipeNode.builder(nodeId, categoryId)
                .inputs(inputs)
                .outputs(outputs)
                .outputCount(outputCount)
                .station(resolvedStation)
                .processingTime(time)
                .energyCost(energy)
                .grid(gridWidth, gridHeight)
                .unlockCondition(unlockCondition)
                .sources(sources)
                .build();
        if (AtlasApi.isReady() && AtlasApi.get().recipeGraph() == graph) {
            AtlasApi.get().addRecipe(node);
        } else {
            graph.addNode(node);
        }
    }
}
