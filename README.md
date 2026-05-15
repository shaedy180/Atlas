# Atlas

Atlas is a recipe and item intelligence mod for Minecraft 26.1 on Fabric.

Atlas goes beyond flat recipe cards. It builds a synced runtime registry of categories, recipes, acquisition sources and info pages, then uses that data for in-game search, recipe browsing and mod integration.

<img width="1919" height="1079" alt="Screenshot 2026-04-09 130153" src="https://github.com/user-attachments/assets/6f2744fa-f869-4a8c-af54-de2c8b673e3b" />

<img width="1186" height="1075" alt="image" src="https://github.com/user-attachments/assets/2f72dafd-aa8d-41d7-b82d-6602e5b2b309" />

## Current features

- Quick Mode overlay on inventory screens with resize support, search, recipe preview, save pins and crafting paste helpers.
- Deep Mode screen with Craft, Use, Sources, Alternatives, Unlocks and Notes tabs.
- Shared search behavior in Quick and Deep Mode.
- Search prefixes:
  `@mod`, `$tag`, `#tooltip`, `>source`, `=station`, `~unlocked`, `!hidden`, `*renewable`
- Availability evaluation with explicit `UNKNOWN` fallback when progression data is not synchronized yet.
- Server-built registry snapshot synchronized to clients.
- Mod API v1 surface for categories, recipes, sources, info pages and renderer bindings.

## Current API status

Atlas now registers integration data through a scoped registration context:

```java
public final class ExampleAtlasPlugin implements AtlasPlugin {
    @Override
    public void register(AtlasRegistrationContext ctx) {
        ctx.categories().add("example:alloying").name("Alloying").register();
        ctx.recipes().add("example:bronze_alloy", "example:alloying")
                .output(new EntryKey("item", "example:bronze_ingot"))
                .register();
    }
}
```

Full API documentation and a larger example live in [README_API.md](README_API.md).

## Still in progress

- Full server-to-client progression sync for advancements and pack-specific progression.
- Rich custom renderer execution in the UI. Renderer bindings are registered and synced already, but default rendering is still used.
- Broader non-vanilla source loaders such as villager trades, loot tables and worldgen extraction.
- Tree-style recipe graph planning UI.
- NeoForge support.

## Installation

Requires:

- Minecraft 26.1+
- Fabric Loader 0.18.4+
- Fabric API 0.144+

Drop the jar into your `mods` folder.

## Keybinds

| Key | Action |
|-----|--------|
| O | Toggle Quick Mode panel |
| U | Open Deep Mode |

Both can be rebound in the controls menu under the Atlas category.

## Building from source

```bash
./gradlew test build
```

The output jar will be in `atlas-loader-fabric/build/libs/`.

## Links

- Source code: [github.com/shaedy180/Atlas](https://github.com/shaedy180/Atlas)
- Issues: [github.com/shaedy180/Atlas/issues](https://github.com/shaedy180/Atlas/issues)
- Donate: [ko-fi.com/shaedy](https://ko-fi.com/shaedy)

## License

LGPL-3.0-or-later
