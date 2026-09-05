# Changelog

## 2.9.0 — Minecraft 26.2

- Added NBS Workshop → Restore ENB for already-pasted Paper projection Litematics, including 2.8.x metadata.
- Added transmitter-anchored translation, clockwise rotation and source-axis mirroring.
- Added a shared bounded upload protocol and Paper Server 0.9.0 carrier preflight, permission/world/chunk validation, result feedback and YAML persistence.
- Restored note settings, pitch cents, object identities and projection timelines; retained pitch cents during later GUI and wand edits.
- Added protocol/transform tests, real packaged-client export/import and GUI checks, and MockBukkit integration tests driving the real plugin and persisted data.
- Full Fabric exports and Paper vanilla Registry safety remain intact. Recovery is an explicit action after pasting; automatic Easy Place hooks and direct Workshop → Receiver uploads remain future work.

See the [release notes](docs/releases/2.9.0.md).

## 2.8.1 — Minecraft 26.2

- Fixed Paper Client startup `IllegalClassLoadError` by isolating `BridgeSoundEngineMixin` in a dedicated Mixin package.
- Kept the full MIDI pitch range and limited the 48-block attenuation override to ENB sounds.
- Added JAR package-ownership regression checks and a production Fabric client startup test covering the note editor, vanilla registries, all 128 MIDI pitch factors and vanilla sound behavior.
- Fixed missing Visuals pack metadata that caused Minecraft to disable the built-in pack; updated bundled/generated sound-pack metadata to format 88. The startup test now waits for resource loading and checks the active item selector.
- Updated Paper Server to 0.8.2: GUI saves require `extendednoteblockbridge.use`, validate the exact payload length and reject distant/unloaded/out-of-height targets before accessing blocks. Existing saved data and channels remain compatible.
- Added server-side payload tests for signed positions, MIDI range, settings bounds and reach validation.

See the [release notes](docs/releases/2.8.1.md).

### Repository maintenance included in 2.8.1

- Restored installation and usage documentation, and separated the Paper architecture, development guide and roadmap from the project overview.
- Updated the Chinese, English and Japanese documentation to the Minecraft 26.2 build pipeline and three-edition packaging.
- Archived inactive upstream 1.20.1 / 1.21.1 files and the obsolete publishing script under `legacy/`, preserving their contents.
- Corrected the Gradle project name to `ExtendedNoteBlock-26.2`. The earlier documentation/archive cleanup did not change the 2.8.0 binaries.

## 2.8.0 — Minecraft 26.2

- Added a Full-style Paper note editor with GM instrument selection and a scrollable 128-key piano.
- Added a registry-safe Paper SoundEngine mixin to address the low-note pitch-clamping issue; in-game listening verification remains pending.
- Switched Paper Projection Litematic output to vanilla carriers and retained ENB metadata. Automatic restoration after pasting is not yet implemented.
- Paper Server remains at 0.8.1.

See the [release notes](docs/releases/2.8.0.md).

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
