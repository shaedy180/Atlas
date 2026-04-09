# Contributing to Atlas

Thanks for your interest in contributing to Atlas! This document explains how to get started.

## Prerequisites

- Java 25 or later
- Gradle (wrapper included, no manual install needed)
- Minecraft 26.1.1 with Fabric Loader 0.18.6

## Building

```bash
git clone https://github.com/shaedy180/Atlas.git
cd Atlas
./gradlew build
```

The build output will be in `atlas-loader-fabric/build/libs/`.

## Project Structure

Atlas is split into multiple modules:

- `atlas-core` -- Pure data model, no Minecraft dependencies
- `atlas-api` -- Public API for mod integrations
- `atlas-search` -- Search subsystem (index, query parsing)
- `atlas-ui` -- UI abstractions
- `atlas-loader-fabric` -- Fabric mod entrypoint, depends on all other modules

## Making Changes

### Branch Naming

Use the following prefixes for your branches:

- `feature/` -- New features or enhancements
- `fix/` -- Bug fixes
- `docs/` -- Documentation changes
- `chore/` -- Maintenance, dependency updates, CI changes

Example: `feature/add-brewing-recipes` or `fix/search-crash-on-empty-query`

### Workflow

1. Fork the repository
2. Create a branch from `main` using the naming convention above
3. Make your changes
4. Run `./gradlew build` and make sure it compiles without warnings
5. Submit a pull request against `main`

### Code Style

- Follow standard Java conventions
- No wildcard imports
- Keep classes focused and small
- Write meaningful commit messages

### Commit Messages

Use clear, descriptive commit messages. Prefix with the module name when the change is scoped to one module:

```
atlas-search: fix query parser handling of quoted strings
atlas-core: add recipe graph cycle detection
```

For cross-cutting changes, a plain description is fine.

## Reporting Issues

Please use the [issue templates](https://github.com/shaedy180/Atlas/issues/new/choose) when reporting bugs or requesting features.

## License

By contributing to Atlas, you agree that your contributions will be licensed under the [LGPL-3.0-or-later](LICENSE) license.
