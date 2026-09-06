# Changelog

## Unreleased — Minecraft 26.2

## 2.12.0 — Minecraft 26.2

- Removed the resource-pack-only fake world-block renderer and its packet refresh/indexing path; no-Mod players again see the real vanilla Paper carriers.
- Retained ENB inventory item models and mandatory-by-default listener audio in one automatically delivered Server Resources pack.
- Removed the redundant standalone Visuals ZIP from builds and releases while keeping Paper Client's built-in item selectors.
- Doubled melodic sampling density from 11 octave anchors to 22 half-octave anchors, reducing normal vanilla-client pitch shifting to about three semitones.
- Raised Vorbis quality to 5, added high-precision SoXR fallback resampling and a short trimmed-tail fade, and enforced a 50,000,000-byte release ceiling.
- Avoided destructive stereo phase cancellation by deriving positional mono audio and normalization from the centered renderer's left main channel.

See the [release notes](docs/releases/2.12.0.md).

## 2.11.0 — Minecraft 26.2

- Added entity-free placed ENB visuals for unmodified clients that accept the automatically delivered server resource pack.
- Rendered all six OFF faces with `a_top.png` and all six powered faces with full-bright `a_top_on.png`; the real Paper carrier remains a Note Block.
- Added per-player chunk-indexed multi-block updates and lifecycle restoration without mutating world blocks or reserving normal Redstone Lamp states.
- Kept Paper Client on its original pitch-specific renderer and skipped/removed the simplified listener pack when the Mod channel is detected.
- Added exhaustive Note Block fallback validation so only the two reserved packet-only states select ENB models.

See the [release notes](docs/releases/2.11.0.md).

## 2.10.1 — Minecraft 26.2

- Fixed automatic listener-pack delivery after upgrades by reading release-managed URL, UUID and SHA-1 metadata directly from the installed Paper Server JAR instead of allowing an old on-disk config to shadow it.
- Delayed the join request until login settles, added visible player/server status messages, a 15-second missing-status warning, and `/enb pack status|resend` diagnostics.
- Normalized physical listener samples and rejected effectively inaudible encodes so quiet extreme-register SoundFont notes no longer appear to leave gaps in MIDI 0–127 coverage.
- Added release-metadata migration tests, all-note resolver checks and release-JAR validation for both default config and embedded metadata.

See the [release notes](docs/releases/2.10.1.md).

## 2.10.0 — Minecraft 26.2

- Added an automatically delivered combined visual and listener resource pack for unmodified Paper/Purpur clients.
- Added 32 representative GM timbres with 11 octave anchors each, covering MIDI 0–127 within vanilla's pitch range, plus 47 independent percussion samples.
- Added eight logical aliases per sound for overlapping-note stop control without duplicating OGG data.
- Paper Server 0.10.0 tracks resource-pack load status, routes modded and unmodified listeners separately, preserves vanilla fallback on failure, and stops custom sounds with normal note/projection lifetimes.
- Release automation now builds the deterministic resource pack, injects its exact GitHub asset URL and SHA-1 into the Paper plugin, and validates both artifacts.

See the [release notes](docs/releases/2.10.0.md).

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
