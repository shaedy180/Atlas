package dev.atlasmod.api.registration;

import dev.atlasmod.core.category.RecipeCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fluent builder for registering recipe categories with Atlas.
 *
 * Pending registrations accumulate in a synchronized list and are drained
 * by the Atlas runtime during initialization. This is safe for concurrent
 * registration from multiple mod entrypoints.
 */
public final class CategoryRegistration {

    private static final List<RecipeCategory> PENDING = Collections.synchronizedList(new ArrayList<>());

    private final String id;
    private String name;
    private String icon;
    private int order = 100;
    private final List<String> stations = new ArrayList<>();
    private final List<String> recipeTypes = new ArrayList<>();

    public CategoryRegistration(String id) {
        this.id = id;
    }

    public CategoryRegistration name(String name) {
        this.name = name;
        return this;
    }

    public CategoryRegistration icon(String icon) {
        this.icon = icon;
        return this;
    }

    public CategoryRegistration order(int order) {
        this.order = order;
        return this;
    }

    /**
     * Blocks or machines that run this category, e.g. your alloy furnace.
     * The first one is the default station of every recipe in the category and
     * all of them list the category in their Uses tab.
     */
    public CategoryRegistration station(String... stationIds) {
        stations.addAll(List.of(stationIds));
        return this;
    }

    /**
     * Game recipe types that Atlas should import into this category on its own.
     * Their recipes are read through the vanilla recipe display system, so no
     * further code is needed if your recipes implement {@code display()}.
     */
    public CategoryRegistration recipeType(String... recipeTypeIds) {
        recipeTypes.addAll(List.of(recipeTypeIds));
        return this;
    }

    public void register() {
        if (name == null) name = id;
        PENDING.add(new RecipeCategory(id, name, icon, order, stations, recipeTypes));
    }

    public static List<RecipeCategory> drainPending() {
        synchronized (PENDING) {
            List<RecipeCategory> result = List.copyOf(PENDING);
            PENDING.clear();
            return result;
        }
    }
}
