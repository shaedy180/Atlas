# Atlas

A recipe and item intelligence mod for Minecraft 26.1.1 (Fabric).

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

Requires:
- Minecraft 26.1.1
- Fabric Loader 0.18.6
- Fabric API 0.145.4 for 26.1.1

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


## Development and release

Use Java 25 and the included wrapper. Modules: `atlas-core` data model, `atlas-api` integrations, `atlas-search` search, `atlas-ui` abstractions, and `atlas-loader-fabric` runtime. Read [AGENTS.md](AGENTS.md). Changes use `working-branch` → `main`; CI validates the wrapper, builds all modules and uploads jars. The manual release workflow builds a verified main revision and creates a versioned GitHub release. Install jars in the matching Minecraft/Fabric `mods` folder; no VPS deployment applies. Preserve saves and the previous compatible jar for rollback. Historical experimental work is retained through recovery tags, not implicitly released. Contributions retain the LGPL-3.0-or-later license.

# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.2.x   | Yes                |
| < 0.2   | No                 |

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

Release-specific builds, tag checks, immutable commit labels, publishing and deployment safeguards remain in place. Main publication starts only after the verification gate succeeds; the application test suite is not repeated after merge. Manual and tag/release validation remain available. Keep both the existing required checks and **CI provenance** required in branch protection.
