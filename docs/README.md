<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Docucraft documentation

Technical documentation of the app as it is now: how each part works, and why it was built that
way. The rules for working on the code are in [`AGENTS.md`](../AGENTS.md). The history of how each
part got here is in git.

| Document | Covers |
|---|---|
| [architecture.md](architecture.md) | Modules, layers, ports, results instead of exceptions, ViewModels, DI |
| [navigation.md](navigation.md) | One back stack, scenes (overlays, list-detail), modal destinations |
| [scanning.md](scanning.md) | The scanning contract, ML Kit, process death, saving, the catalogue |
| [database.md](database.md) | The catalogue's tables and rules, migrations, the bin, upkeep and reconciliation, decisions DB1 to DB11 |
| [organization.md](organization.md) | Folders, tags and favorites: the palette, Home's sections, the destinations that organize the library |
| [pdf-engine.md](pdf-engine.md) | `:composepdf`: rendering, layout, gestures, extension points, reading position |
| [pdf-viewer.md](pdf-viewer.md) | The viewer feature: screen, settings, details, errors, the external viewer |
| [text-and-links.md](text-and-links.md) | Page content, text selection, OCR readiness, safe links |
| [testing.md](testing.md) | What is tested where, running tests, fixtures, checking by hand |

## Decisions referenced in the code

Comments in the code cite these by name. Each is explained where it lives.

| Name | Decision | Where |
|---|---|---|
| D1 | Below API 35, reading text degrades: no second PDF library, no higher `minSdk` | [text-and-links.md](text-and-links.md#providers) |
| D2 | App-wide display defaults, plus per-document memory for the session; A1: a process death is the same session; A2: the factory setting is fit to width | [pdf-viewer.md](pdf-viewer.md#display-settings-d2) |
| D3 | A link shows its real destination before anything opens | [text-and-links.md](text-and-links.md#showing-d3) |
| D4 | Links open in a Custom Tab, then `ACTION_VIEW`, then a message | [text-and-links.md](text-and-links.md#opening-d4) |
| D5 | The external viewer has its own task and its own back stack | [pdf-viewer.md](pdf-viewer.md#the-external-viewer-d5) |
| DB1–DB11 | The catalogue: search engine, Recents, other apps' documents, folders, Home's sections, the bin, text recognition, backups, limits, reading position, language | [database.md](database.md#decisions) |
| E1–E6 | The engine's restoration, gesture consumption, claimable gestures, overlay scope, scroll-to-point and content padding | [pdf-engine.md](pdf-engine.md) |

## Status

The app has been through a stabilization, one subsystem at a time.

| Subsystem | State                                                                                                                                      |
|---|--------------------------------------------------------------------------------------------------------------------------------------------|
| Scanning and the catalogue | Done. The process-death paths are still to be checked on a device ([scanning.md](scanning.md#not-yet-verified-on-a-device)).               |
| Domain boundaries | Done in the features. `core/domain` keeps two Compose types, which is pending with preferences.                                            |
| Navigation | Done.                                                                                                                                      |
| PDF viewer, text and links | Done. Still to check on a phone and a tablet: the whole viewer, TalkBack end to end, and opening links with a browser without Custom Tabs. |
| Database | Done. The catalogue is on schema version 5, reached by a migration that keeps every document ([database.md](database.md)). Documents the app keeps and documents it only refers to, files named by uuid, ranked search over names and page text ([scanning.md](scanning.md)), Recents and reading position ([pdf-viewer.md](pdf-viewer.md#reading-position)), text recognition on the device, folders, tags and favorites ([organization.md](organization.md)), and a bin with daily upkeep. Still to check on a phone: an upgrade from version 4 with real documents, and recognition below API 35. |
| Preferences and theme | Done. The theme changes smoothly with the tradeoff of "screenshotting" the app                                                             |
| Search and filtering | Pending                                                                                                                                    |
| Analytics | Pending                                                                                                                                    |

## How a subsystem is stabilized

The method used so far, for the next ones:
1. **Map.** Trace the flow file by file, from the user's gesture to its effect. Mark every layer
   boundary it crosses, and what type crosses it.
2. **Find the coupling.** Where do an SDK's types appear? Does the SDK dictate the *shape* of a
   contract? What does it do for us that would vanish if it were replaced?
3. **Diagnose.** Prioritize by what each problem blocks today, not by how ugly it is. Write down the
   bugs found on the way: there always are some. The acid test: can the critical step be tested on
   the JVM?
4. **Design.** Name the one key decision; the rest follows from it. Then:
   - a port in the domain, with its own models;
   - sealed results;
   - room in the contract for what another implementation would need;
   - a swap point that is one line of DI.
5. **Migrate in small steps.**
   - Tests first, including a failing one for a bug found.
   - Delete dead code before refactoring.
   - Add the new thing unwired, then wire it.
   - Isolate the riskiest step in its own commit. Never fix a bug and move code in the same commit.
   - Verify each step on a device, and write down what was verified.
6. **Close.** Update `AGENTS.md` if a rule changed, and update the documentation here.

Each stabilization goes on its own branch (`refactor/<subsystem>`), merged through a pull request.

## Conventions

- **Language.** Documentation is in English.
- **What it describes.** The current state. When the code changes, the document changes with it:
  no dated snapshots, no step-by-step plans, no bug numbers.
- **The why.** Explain the reason for a decision, briefly, next to the decision.
- **Detail.** Code-level detail belongs in the KDoc. These documents point to the class instead of
  repeating it.
