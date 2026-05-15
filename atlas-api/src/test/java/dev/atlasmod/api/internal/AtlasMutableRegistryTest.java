package dev.atlasmod.api.internal;

import dev.atlasmod.api.AtlasRegistrationContext;
import dev.atlasmod.core.entry.EntryKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtlasMutableRegistryTest {

    @Test
    void rejectsDuplicateCategoryIds() {
        AtlasMutableRegistry registry = new AtlasMutableRegistry();
        AtlasRegistrationContext context = new AtlasRegistrationContextImpl("mymod", registry);

        context.categories().add("mymod:machines").name("Machines").register();

        assertThrows(IllegalArgumentException.class, () ->
                context.categories().add("mymod:machines").name("Machines Again").register());
    }

    @Test
    void buildsSnapshotWithOwnedRecipe() {
        AtlasMutableRegistry registry = new AtlasMutableRegistry();
        AtlasRegistrationContext context = new AtlasRegistrationContextImpl("mymod", registry);

        context.categories().add("mymod:machines").name("Machines").register();
        context.recipes().add("mymod:bronze_alloy", "mymod:machines")
                .output(new EntryKey("item", "mymod:bronze_ingot"))
                .register();

        assertEquals("mymod", registry.snapshot().recipesById().get("mymod:bronze_alloy").ownerModId());
    }
}
