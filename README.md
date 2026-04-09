# Atlas

A recipe and item intelligence mod for Minecraft 26.1 (Fabric).

Atlas goes beyond the typical recipe viewer. Instead of just showing you flat recipe cards, it gives you a complete picture of how to get any item in the game, which recipes use it, where it comes from, and what alternatives exist. It works with vanilla and modded content out of the box.

<img width="1919" height="1079" alt="Screenshot 2026-04-09 130153" src="https://github.com/user-attachments/assets/6f2744fa-f869-4a8c-af54-de2c8b673e3b" />

<img width="1186" height="1075" alt="image" src="https://github.com/user-attachments/assets/2f72dafd-aa8d-41d7-b82d-6602e5b2b309" />


## What it does

**Quick Mode** shows up as a resizable panel on the side of your inventory. Browse items, search by name, mod, tag or tooltip, and see recipes right there without leaving your crafting table. It stays out of your way but is always one keypress away.

**Deep Mode** is a full-screen interface (press U) with a browsable item list on the left and detailed recipe information on the right. Tabs let you switch between how to craft something, what it is used in, and all the ways you can obtain it.

**Search** supports prefixes like `@modname` to filter by mod, `$tag` to filter by item tag, and `#text` to search inside tooltips. Filter buttons are auto-detected based on which mods are installed.

**Save items** you care about so you can find them quickly later. The Saved filter narrows the list to just your bookmarked items.

**Creative mode** lets you click any item in the Atlas UI to add it directly to your inventory.

## Coming soon

- **Availability Engine** that explains why you can not make something yet (missing workstation, wrong dimension, locked progression)
- **Alternative Resolver** showing tag-based substitutions and marking the cheapest or simplest options
- **Recipe graph** with tree views, intermediate requirements, cycle detection, and material totals
- **Pinned build plans** that track what you still need and what you already have
- **Notes and info pages** for pack-specific tips and item details
- **NeoForge support**
- **Mod API** so other mods can register their machines and custom recipe types with zero effort

## Installation

Requires Minecraft 26.1.1, Fabric Loader 0.18.4+, and Fabric API.

Drop the jar into your `mods` folder.

## Keybinds

| Key | Action |
|-----|--------|
| O | Toggle Quick Mode panel |
| U | Open Deep Mode |

Both can be rebound in the controls menu under the Atlas category.

## Building from source

```
./gradlew build
```

The output jar will be in `atlas-loader-fabric/build/libs/`.

## Links

- Source code: [github.com/shaedy180/Atlas](https://github.com/shaedy180/Atlas)
- Donate: [ko-fi.com/shaedy](https://ko-fi.com/shaedy)

## Feedback

If you run into a bug, please open an issue on GitHub. I am happy to look into it.

Feature requests and mod support suggestions are also welcome. If there is a mod you want Atlas to work better with, let me know in the issues.

If you want to support development, donations at [ko-fi.com/shaedy](https://ko-fi.com/shaedy) are very much appreciated.

## License

LGPL-3.0-or-later
