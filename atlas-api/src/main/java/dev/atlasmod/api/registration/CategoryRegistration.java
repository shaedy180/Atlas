package dev.atlasmod.api.registration;

import dev.atlasmod.core.category.RecipeCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for registering recipe categories with Atlas.
 */
public final class CategoryRegistration {

    // Static registry — will be consumed by the Atlas runtime
    private static final List<RecipeCategory> PENDING = new ArrayList<>();

    private final String id;
    private String name;
    private String icon;
    private int order = 100;

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

    public void register() {
        if (name == null) name = id;
        PENDING.add(new RecipeCategory(id, name, icon, order));
    }

    public static List<RecipeCategory> drainPending() {
        List<RecipeCategory> result = List.copyOf(PENDING);
        PENDING.clear();
        return result;
    }
}
