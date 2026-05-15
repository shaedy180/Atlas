package dev.atlasmod.api.registration;

import dev.atlasmod.api.internal.AtlasRegistrationSink;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;
import dev.atlasmod.core.recipe.RecipeNode;
import dev.atlasmod.core.unlock.UnlockCondition;
import dev.atlasmod.core.visibility.VisibilityPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for registering recipes with Atlas.
 */
public final class RecipeRegistration {

    private final AtlasRegistrationSink sink;
    private final String ownerModId;
    private final String categoryId;
    private String id;
    private final List<IngredientKey> inputs = new ArrayList<>();
    private final List<EntryKey> outputs = new ArrayList<>();
    private StationKey station;
    private int time;
    private int energy;
    private int gridWidth;
    private int gridHeight;
    private UnlockCondition unlockCondition;
    private VisibilityPolicy visibility = VisibilityPolicy.VISIBLE;
    private final List<String> searchAliases = new ArrayList<>();
    private String rendererKey;

    public RecipeRegistration(AtlasRegistrationSink sink, String ownerModId, String id, String categoryId) {
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
        this.ownerModId = Objects.requireNonNull(ownerModId, "ownerModId must not be null");
        this.id = id;
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

    public RecipeRegistration grid(int width, int height) {
        this.gridWidth = width;
        this.gridHeight = height;
        return this;
    }

    public RecipeRegistration unlock(UnlockCondition condition) {
        this.unlockCondition = condition;
        return this;
    }

    public RecipeRegistration visibility(VisibilityPolicy visibility) {
        this.visibility = Objects.requireNonNull(visibility, "visibility must not be null");
        return this;
    }

    public RecipeRegistration searchAlias(String alias) {
        this.searchAliases.add(Objects.requireNonNull(alias, "alias must not be null"));
        return this;
    }

    public RecipeRegistration searchAliases(String... aliases) {
        for (String alias : aliases) {
            searchAlias(alias);
        }
        return this;
    }

    public RecipeRegistration renderer(String rendererKey) {
        this.rendererKey = rendererKey;
        return this;
    }

    public void register() {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Recipe id must be set before register()");
        }
        if (outputs.isEmpty()) {
            throw new IllegalStateException("Recipe " + id + " must declare at least one output");
        }
        sink.addRecipe(RecipeNode.builder(id, ownerModId, categoryId)
                .inputs(inputs)
                .outputs(outputs)
                .station(station)
                .processingTime(time)
                .energyCost(energy)
                .grid(gridWidth, gridHeight)
                .unlockCondition(unlockCondition)
                .visibilityPolicy(visibility)
                .searchAliases(searchAliases)
                .rendererKey(rendererKey)
                .build());
    }
}
