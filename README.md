# Atlas

A recipe and item intelligence mod for Minecraft 26.1.1 to 26.3 (Fabric and NeoForge).

Atlas goes beyond the typical recipe viewer. Instead of just showing you flat recipe cards, it gives you a complete picture of how to get any item in the game, which recipes use it, where it comes from, and what alternatives exist. It works with vanilla and modded content out of the box.

<img width="1919" height="1079" alt="Screenshot 2026-04-09 130153" src="https://github.com/user-attachments/assets/6f2744fa-f869-4a8c-af54-de2c8b673e3b" />

<img width="1186" height="1075" alt="image" src="https://github.com/user-attachments/assets/2f72dafd-aa8d-41d7-b82d-6602e5b2b309" />


## What it does

**Quick Mode** shows up as a resizable panel on the side of your inventory. Browse items, search by name, mod, tag or tooltip, and see recipes right there without leaving your crafting table. It stays out of your way but is always one keypress away.

**Deep Mode** is a full-screen interface (press U) with a browsable item list on the left and detailed recipe information on the right. Tabs let you switch between how to craft something, what it is used in, all the ways you can obtain it, the full crafting tree, alternatives, unlock requirements and notes. Click any item in a recipe to jump to it, Backspace goes back.

**Search** supports prefixes like `@modname` to filter by mod, `$tag` to filter by item tag, and `#text` to search inside tooltips. Filter buttons are auto-detected based on which mods are installed.

**Save items** you care about so you can find them quickly later. The Saved filter narrows the list to just your bookmarked items.

**Creative mode** lets you click any item in the Atlas UI to add it directly to your inventory.

**Why can't I make this?** Every recipe tells you what is in the way: a missing workstation nearby, the wrong dimension or biome, a recipe you have not unlocked yet, or the exact materials you are short of. Quick Mode shows this as a small colored dot next to each recipe.

**Crafting tree** (Tree tab) expands an item down to raw materials. It uses what is already in your inventory, keeps leftovers like the extra planks from a log, stops at loops such as ingots and blocks, and adds up the materials and crafting steps.

**Alternatives** lists every recipe for an item with the cheapest and simplest one marked, every item a tag slot accepts (owned ones highlighted), and items that can be swapped in.

**Build plans** are pinned from the Tree tab. They show up next to your inventory with progress and the materials still missing, and `/atlas plans` prints them in chat.

**Notes** keep your own notes per item, next to item details and info pages from resource packs or mods.

## Installation

Pick the jar that matches your Minecraft version and loader and drop it into your `mods` folder.

| Minecraft | Fabric | NeoForge |
|-----------|--------|----------|
| 26.1.1 | Fabric API 0.145.4 | not available (NeoForge only has betas) |
| 26.1.2 | Fabric API 0.155.3 | NeoForge 26.1.2.129 |
| 26.2 | Fabric API 0.161.0 | NeoForge 26.2.0.95 |
| 26.3 | Fabric API 0.167.0 | NeoForge 26.3.0.116 |

Fabric needs Fabric Loader 0.18.4 or newer (0.19.3 for 26.3). Atlas is client side only. Recipes, the recipe book and advancements are read in singleplayer.

## Keybinds

| Key | Action |
|-----|--------|
| O | Toggle Quick Mode panel |
| U | Open Deep Mode |

Both can be rebound in the controls menu under the Atlas category.

## Building from source

```
./gradlew build                  # latest Minecraft version (26.3)
./gradlew build -Pmc=26.1.1      # one specific version
scripts/build-all.sh             # every version, jars end up in build/dist/
```

Each supported version has a file in `versions/` with its Minecraft, Fabric API and NeoForge versions. Single builds put the jars in `atlas-loader-fabric/build/libs/` and `atlas-loader-neoforge/build/libs/`.

`./gradlew :atlas-loader-fabric:runClientGameTest -Pmc=<version>` starts a real client, creates a world, checks recipe loading, the planner and the API, and saves screenshots of every screen. It needs a display.

## Mod API

Other mods can feed Atlas their machines, recipe types, recipes and tips. Add the Atlas jar as a compile only dependency; everything lives in `dev.atlasmod.api`.

**Getting called.** On Fabric, list your plugin under the `atlas` entrypoint in `fabric.mod.json`:

```json
"entrypoints": { "atlas": ["com.example.mymod.MyAtlasPlugin"] }
```

On NeoForge, annotate the class with `@AtlasEntrypoint` and give it a public no-argument constructor. Either way the class implements `AtlasPlugin`:

```java
public class MyAtlasPlugin implements AtlasPlugin {
    @Override
    public void onAtlasReady() {
        // register categories, recipes and info pages here
    }

    @Override
    public void onWorldLoad() {
        // optional: world or datapack dependent recipes, removed again when the world is left
    }
}
```

**Machines and recipe types.** If your recipes implement `display()` like vanilla ones, Atlas already imports them: unknown recipe types get their own category. Registering the category gives it a proper name, icon and station:

```java
AtlasApi.category("mymod:alloy_smelting")
        .name("Alloy Smelting")
        .icon("mymod:alloy_furnace")
        .station("mymod:alloy_furnace")      // machine that runs it, also lists the category in its Uses tab
        .recipeType("mymod:alloy_smelting")  // game recipe type to import into this category
        .register();
```

**Recipes by hand.** For recipes that do not live in the recipe manager:

```java
AtlasApi.recipes("mymod:alloy_smelting")
        .id("mymod:bronze")
        .input(IngredientKey.item("minecraft:copper_ingot"), 3)
        .display(IngredientKey.tag("c:ingots/tin"))
        .output(new EntryKey("item", "mymod:bronze_ingot"), 4)
        .time(200)
        .energy(1200)
        .require(new UnlockCondition.RequiresDimension("minecraft:the_nether"))
        .register();
```

Without `.station(...)` the category's first station is used. Recipes registered in `onAtlasReady()` stay across world reloads. Use `.grid(width, height)` for shaped grids, with `IngredientKey.EMPTY` for gaps.

**Unlock conditions** decide what the Unlocks tab and the "why can't I make this" hints say: `RequiresStation`, `RequiresDimension`, `RequiresBiome` (an id, or a tag written as `#minecraft:is_forest`), `RequiresAdvancement`, `RequiresProgression`, `RequiresCatalyst`, `Custom` (always blocks) and `Check`, which runs your own test against the player's context. Several conditions are combined with `require(...)` or `UnlockCondition.and(...)`. Stage mods can report their stages with `AtlasApi.stageProvider(...)`, they satisfy `RequiresProgression` with the same key.

**Other sources.** Mob drops, machine byproducts and the like go into the Sources tab:

```java
AtlasApi.addSource("minecraft:flint", new AcquisitionSource(
        AcquisitionSource.SourceType.BLOCK_DROP, "Gravel", "10% chance when broken"));
```

**Info pages** show up in the Notes tab. From code:

```java
AtlasApi.info("mymod:tin_tips")
        .title("Finding tin")
        .line("Tin ore spawns below Y 40 in mountain biomes.")
        .forItems("mymod:raw_tin", "#c:ores/tin")
        .register();
```

Mods and resource packs can also ship them as JSON at `assets/<namespace>/atlas/info/<name>.json`, which is handy for modpacks:

```json
{
  "title": "Iron in this pack",
  "items": ["minecraft:iron_ingot", "#c:ores/iron"],
  "lines": ["Iron is rare here, use the ore washer from Create."]
}
```

Use `AtlasApi.isReady()` if Atlas is an optional dependency. `/atlas debug` shows how many recipes, categories, info pages and plugins Atlas picked up.

## Links

- Source code: [github.com/shaedy180/Atlas](https://github.com/shaedy180/Atlas)
- Donate: [ko-fi.com/shaedy](https://ko-fi.com/shaedy)

## Feedback

If you run into a bug, please open an issue on GitHub. I am happy to look into it.

Feature requests and mod support suggestions are also welcome. If there is a mod you want Atlas to work better with, let me know in the issues.

If you want to support development, donations at [ko-fi.com/shaedy](https://ko-fi.com/shaedy) are very much appreciated.

## License

LGPL-3.0-or-later


## Development and release

Use Java 25 and the included wrapper. Modules: `atlas-core` data model and analysis (availability, planner, alternatives), `atlas-api` integrations, `atlas-search` search, `atlas-ui` persisted UI state, `atlas-common` the shared client code with small per-version shims in `src/compat`, and `atlas-loader-fabric` / `atlas-loader-neoforge` as thin loader entry points. Read [AGENTS.md](AGENTS.md). Changes use `working-branch` → `main`; CI validates the wrapper, builds and tests every version in `versions/` with `scripts/build-all.sh` and uploads the jars. The manual release workflow builds a verified main revision for every version and creates a versioned GitHub release with all jars. Install jars in the matching Minecraft/Fabric `mods` folder; no VPS deployment applies. Preserve saves and the previous compatible jar for rollback. Historical experimental work is retained through recovery tags, not implicitly released. Contributions retain the LGPL-3.0-or-later license.

# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.3.x   | Yes                |
| < 0.3   | No                 |

## Reporting a Vulnerability

If you discover a security vulnerability in Atlas, please report it responsibly.

**Do not open a public issue for security vulnerabilities.**

Instead, send an email to **security@shaedy.de** with the following information:

- A description of the vulnerability
- Steps to reproduce the issue
- The potential impact
- Any suggested fix, if you have one

You should receive an initial response within 48 hours. We will work with you to understand the issue and coordinate a fix before any public disclosure.

## Scope

This policy applies to the Atlas mod and its published artifacts. Third-party mods that integrate with the Atlas API are outside the scope of this policy.

## Disclosure

Once a fix is available, we will publish a security advisory on GitHub and release a patched version. Credit will be given to the reporter unless they prefer to remain anonymous.


# Contributor Covenant Code of Conduct

## Our Pledge

We as members, contributors, and leaders pledge to make participation in our
community a harassment-free experience for everyone, regardless of age, body
size, visible or invisible disability, ethnicity, sex characteristics, gender
identity and expression, level of experience, education, socio-economic status,
nationality, personal appearance, race, caste, color, religion, or sexual
identity and orientation.

We pledge to act and interact in ways that contribute to an open, welcoming,
diverse, inclusive, and healthy community.

## Our Standards

Examples of behavior that contributes to a positive environment for our
community include:

* Demonstrating empathy and kindness toward other people
* Being respectful of differing opinions, viewpoints, and experiences
* Giving and gracefully accepting constructive feedback
* Accepting responsibility and apologizing to those affected by our mistakes,
  and learning from the experience
* Focusing on what is best not just for us as individuals, but for the overall
  community

Examples of unacceptable behavior include:

* The use of sexualized language or imagery, and sexual attention or advances of
  any kind
* Trolling, insulting or derogatory comments, and personal or political attacks
* Public or private harassment
* Publishing others' private information, such as a physical or email address,
  without their explicit permission
* Other conduct which could reasonably be considered inappropriate in a
  professional setting

## Enforcement Responsibilities

Community leaders are responsible for clarifying and enforcing our standards of
acceptable behavior and will take appropriate and fair corrective action in
response to any behavior that they deem inappropriate, threatening, offensive,
or harmful.

Community leaders have the right and responsibility to remove, edit, or reject
comments, commits, code, wiki edits, issues, and other contributions that are
not aligned to this Code of Conduct, and will communicate reasons for moderation
decisions when appropriate.

## Scope

This Code of Conduct applies within all community spaces, and also applies when
an individual is officially representing the community in public spaces.
Examples of representing our community include using an official email address,
posting via an official social media account, or acting as an appointed
representative at an online or offline event.

## Enforcement

Instances of abusive, harassing, or otherwise unacceptable behavior may be
reported to the community leaders responsible for enforcement at
**security@shaedy.de**.

All complaints will be reviewed and investigated promptly and fairly.

All community leaders are obligated to respect the privacy and security of the
reporter of any incident.

## Enforcement Guidelines

Community leaders will follow these Community Impact Guidelines in determining
the consequences for any action they deem in violation of this Code of Conduct:

### 1. Correction

**Community Impact**: Use of inappropriate language or other behavior deemed
unprofessional or unwelcome in the community.

**Consequence**: A private, written warning from community leaders, providing
clarity around the nature of the violation and an explanation of why the
behavior was inappropriate. A public apology may be requested.

### 2. Warning

**Community Impact**: A violation through a single incident or series of
actions.

**Consequence**: A warning with consequences for continued behavior. No
interaction with the people involved, including unsolicited interaction with
those enforcing the Code of Conduct, for a specified period of time. This
includes avoiding interactions in community spaces as well as external channels
like social media. Violating these terms may lead to a temporary or permanent
ban.

### 3. Temporary Ban

**Community Impact**: A serious violation of community standards, including
sustained inappropriate behavior.

**Consequence**: A temporary ban from any sort of interaction or public
communication with the community for a specified period of time. No public or
private interaction with the people involved, including unsolicited interaction
with those enforcing the Code of Conduct, is allowed during this period.
Violating these terms may lead to a permanent ban.

### 4. Permanent Ban

**Community Impact**: Demonstrating a pattern of violation of community
standards, including sustained inappropriate behavior, harassment of an
individual, or aggression toward or disparagement of classes of individuals.

**Consequence**: A permanent ban from any sort of public interaction within the
community.

## Attribution

This Code of Conduct is adapted from the [Contributor Covenant][homepage],
version 2.1, available at
[https://www.contributor-covenant.org/version/2/1/code_of_conduct.html][v2.1].

Community Impact Guidelines were inspired by
[Mozilla's code of conduct enforcement ladder][Mozilla CoC].

For answers to common questions about this code of conduct, see the FAQ at
[https://www.contributor-covenant.org/faq][FAQ]. Translations are available at
[https://www.contributor-covenant.org/translations][translations].

[homepage]: https://www.contributor-covenant.org
[v2.1]: https://www.contributor-covenant.org/version/2/1/code_of_conduct.html
[Mozilla CoC]: https://github.com/mozilla/diversity
[FAQ]: https://www.contributor-covenant.org/faq
[translations]: https://www.contributor-covenant.org/translations

## Efficient CI and verified releases

Open a draft or ready PR from `working-branch` to `main` to run the complete validation suite. A push to `working-branch` alone does not start CI; use **Run workflow** for checks before opening a PR. New PR updates cancel obsolete runs for that PR. Main and release runs are never cancelled by a newer PR update.

After every required PR job succeeds, **CI provenance** records the tested merge commit. On a `main` push, CI runs only a small verification gate: the latest matching PR run must be successful, its receipt must be current and unexpired, and its tested Git tree and both merge parents must exactly match the actual merge. Missing, failed, stale or mismatched evidence blocks release instead of silently skipping checks. Update the PR against current `main` and wait for all required checks before merging. If a receipt has expired, rerun the complete original PR workflow, then rerun the main gate.

PR validation receipts are kept for 14 days and plain CI build artifacts for 7 days (`retention-days` in the workflows). Merge within 14 days of the last green PR run, otherwise rerun the complete PR workflow first.

Release-specific builds, tag checks, immutable commit labels, publishing and deployment safeguards remain in place. Main publication starts only after the verification gate succeeds; the application test suite is not repeated after merge. Manual and tag/release validation remain available. Keep both the existing required checks and **CI provenance** required in branch protection.
