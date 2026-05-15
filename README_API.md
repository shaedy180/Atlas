# Atlas API v1

Atlas now builds a registry snapshot on the server and syncs it to clients. Third-party mods register Atlas data through `AtlasPlugin#register(AtlasRegistrationContext)`.

## Minimal example

```java
package com.example.mymod;

import dev.atlasmod.api.AtlasRegistrationContext;
import dev.atlasmod.api.plugin.AtlasPlugin;
import dev.atlasmod.core.entry.EntryKey;
import dev.atlasmod.core.entry.IngredientKey;
import dev.atlasmod.core.entry.StationKey;
import dev.atlasmod.core.recipe.AcquisitionSource;
import dev.atlasmod.core.unlock.UnlockCondition;
import dev.atlasmod.core.visibility.VisibilityPolicy;

public final class ExampleAtlasPlugin implements AtlasPlugin {
    @Override
    public void register(AtlasRegistrationContext ctx) {
        ctx.categories().add("example:alloying")
                .name("Alloying")
                .icon("example:alloy_forge")
                .order(35)
                .register();

        ctx.recipes().add("example:bronze_alloy", "example:alloying")
                .display(IngredientKey.tag("c:ingots/copper"), IngredientKey.tag("c:ingots/tin"))
                .output(new EntryKey("item", "example:bronze_ingot"))
                .station(new StationKey("example:alloy_forge"))
                .time(200)
                .energy(1200)
                .unlock(new UnlockCondition.RequiresAdvancement("example:bronze_age"))
                .visibility(VisibilityPolicy.GREYED_OUT)
                .searchAlias("bronze alloy")
                .renderer("example:alloy_progress")
                .register();

        ctx.sources().add("example:bronze_worldgen")
                .entry(new EntryKey("item", "example:bronze_ingot"))
                .type(AcquisitionSource.SourceType.WORLDGEN)
                .description("Found in bronze ore veins")
                .detail("Most common below y=32")
                .renewable(false)
                .register();

        ctx.infoPages().add("example:bronze_notes")
                .entry(new EntryKey("item", "example:bronze_ingot"))
                .title("Bronze")
                .body("Primary mid-game alloy used by Example Mod machines.")
                .reference("example:guide/bronze")
                .register();

        ctx.renderers().add("example:alloy_progress")
                .description("Custom alloying progress renderer")
                .category("example:alloying")
                .register();
    }
}
```

## Fabric entrypoint

Register the plugin in your `fabric.mod.json`:

```json
{
  "entrypoints": {
    "atlas": [
      "com.example.mymod.ExampleAtlasPlugin"
    ]
  }
}
```

## Notes

- Recipe ids must be stable. Atlas no longer generates UUID fallback ids.
- Atlas categories, recipes, sources, info pages and renderer bindings are owned by the registering mod id.
- Search prefixes `>source`, `=station`, `~unlocked`, `!hidden` and `*renewable` are evaluated consistently in Quick Mode and Deep Mode.
