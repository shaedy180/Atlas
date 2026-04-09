package dev.atlasmod.api.registration;

import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;
import dev.atlasmod.core.recipe.RecipeGraph;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.unlock.UnlockCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fluent builder for registering recipes with Atlas (API Stufe 2).
 */
public final class RecipeRegistration {

    private final RecipeGraph graph;
    private final String categoryId;
    private String id;
    private final List<IngredientKey> inputs = new ArrayList<>();
    private final List<EntryKey> outputs = new ArrayList<>();
    private StationKey station;
    private int time;
    private int energy;
    private UnlockCondition unlockCondition;

    public RecipeRegistration(RecipeGraph graph, String categoryId) {
        this.graph = Objects.requireNonNull(graph, "graph must not be null");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
    }

    public RecipeRegistration id(String id) {
        this.id = id;
        return this;
    }

    public RecipeRegistration display(IngredientKey... inputs) {
        this.inputs.addAll(List.of(inputs));
        return this;
    }

    public RecipeRegistration output(EntryKey... outputs) {
        this.outputs.addAll(List.of(outputs));
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

    public RecipeRegistration unlock(UnlockCondition condition) {
        this.unlockCondition = condition;
        return this;
    }

    public void register() {
        String nodeId = this.id != null ? this.id : categoryId + "/" + UUID.randomUUID();
        RecipeNode node = RecipeNode.builder(nodeId, categoryId)
                .inputs(inputs)
                .outputs(outputs)
                .station(station)
                .processingTime(time)
                .energyCost(energy)
                .unlockCondition(unlockCondition)
                .build();
        graph.addNode(node);
    }
}
