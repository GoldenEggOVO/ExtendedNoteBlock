# Changelog

## Unreleased — repository maintenance

- Restored installation and usage documentation, and separated the Paper architecture, development guide and roadmap from the project overview.
- Updated the Chinese, English and Japanese documentation to the Minecraft 26.2 build pipeline and three-edition packaging.
- Archived inactive upstream 1.20.1 / 1.21.1 files and the obsolete publishing script under `legacy/`, preserving their contents.
- Corrected the Gradle project name to `ExtendedNoteBlock-26.2`. Runtime source code and the 2.8.0 release artifacts are unchanged.

## 2.8.0 — Minecraft 26.2

- Added a Full-style Paper note editor with GM instrument selection and a scrollable 128-key piano.
- Added a registry-safe Paper SoundEngine mixin to address the low-note pitch-clamping issue; in-game listening verification remains pending.
- Switched Paper Projection Litematic output to vanilla carriers and retained ENB metadata. Automatic restoration after pasting is not yet implemented.
- Paper Server remains at 0.8.1.

See the [release notes](docs/releases/2.8.0.md) and [published artifacts](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.8.0-mc26.2).

## Earlier 26.2 release notes

- [2.7.1](docs/releases/2.7.1.md)
- [2.6.0](docs/releases/2.6.0.md)
- [2.4.2](docs/releases/2.4.2.md)
- [2.4.1](docs/releases/2.4.1.md)

These historical notes are retained for source history even when older published releases or tags are removed.

## 2.0.2 - 2026-08-08

- Added a dedicated trigger repeater for every vanilla note block so parallel redstone branches activate reliably.

## 2.0.1 - 2026-08-08

- Added vanilla redstone line, straight rail, structure NBT, Litematica and datapack exports.
- Added configurable vanilla instrument support blocks, timing, distribution, minecart and command block options.
- Added NBS tempo-change handling and large-chord-safe datapack playback.
- Fixed generated repeater orientation.

## 1.9.1 - 2026-08-07

- Added NBS workshop preview and searchable song list.
- Added MIDI and common audio file import.
- Added one-to-one projection receivers and centralized long-distance playback.
- Fixed playback synchronization after pausing the game.

## Upstream

Earlier changes belong to the original [Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock) project.
