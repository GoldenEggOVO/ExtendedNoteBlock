# Development Guide · Minecraft 26.2

[Home](../README.md) · [Documentation](README.md) · [简体中文](DEVELOPMENT_zh-cn.md) · [日本語](DEVELOPMENT_ja-jp.md)

This branch targets Minecraft **26.2**. It builds three program editions: Full Fabric, a registry-safe Paper Client, and a Paper / Purpur server plugin. The Paper Client and Full Fabric must not be installed together.

## Toolchain

| Component | Current build baseline |
| --- | --- |
| Java JDK | 25 |
| Gradle wrapper | 9.5.1 |
| Fabric Loom | 1.17.20 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.159.0+26.2 |
| Helper scripts | Python 3 |

Versions come from [gradle.properties](../gradle.properties), the [wrapper configuration](../gradle/wrapper/gradle-wrapper.properties), and [bridge/build.gradle](../bridge/build.gradle). Bridge currently uses the `26.2.build.+` Paper API dependency selector; its resolved build can change between builds.

## Repository layout

| Path | Purpose |
| --- | --- |
| `src/main/` | Full Fabric content, shared music logic and assets |
| `src/client/` | Fabric screens, sound engine and Paper companion code |
| `src/test/` | Existing automated tests |
| `bridge/` | Independent Paper / Purpur Gradle project |
| `scripts/` | 26.2 source preparation and client / resource-pack packaging |
| `.github/workflows/build-26.2.yml` | Build, verification and release workflow |
| `docs/` | Installation, architecture, development, roadmap and release notes |
| `legacy/` | Inactive upstream 1.20.1 / 1.21.1 sources, settings and publishing tool |

The active Gradle build does not include `legacy/`. The old version-switching tasks are not available in this branch.

## Build Full Fabric, Paper Client and resource packs

Run from the repository root with JDK 25 selected. The source-preparation scripts modify tracked files, so use a disposable checkout or a detached worktree for builds when you want to keep your development checkout clean:

```bash
git worktree add --detach ../enb-build port/26.2
cd ../enb-build
```

```bash
python3 scripts/prepare_26_2_sources.py
chmod +x gradlew
./gradlew clean test build --stacktrace
python3 scripts/make_paper_bridge_client_jar.py
python3 scripts/make_server_resource_pack.py
```

The Paper Client packaging script consumes the Full build output and applies a strict class whitelist. Do not replace it with a copy of the Full JAR.

## Build Paper Server

Run from the repository root, in this order:

```bash
python3 scripts/prepare_paper_custom_model_data.py
python3 scripts/prepare_paper_interactions.py
python3 scripts/prepare_paper_render_sync.py
python3 scripts/prepare_paper_listener_pack.py
./gradlew -p bridge clean build --stacktrace
```

`prepare_paper_render_sync.py` also invokes `prepare_paper_command_help.py`. These preparation steps are part of the current build pipeline; compiling the unprepared plugin source alone does not reproduce the release.

On Windows, use `python` if Python 3 is installed under that name, replace `./gradlew` with `.\gradlew.bat`, and omit `chmod`.

| Output directory | Artifact |
| --- | --- |
| `build/libs/` | Full Fabric runtime JAR and sources JAR |
| `build/paper-bridge-client/` | Paper Client JAR |
| `build/server-resource-pack/` | Combined auto-download item + listener ZIP |
| `bridge/build/libs/` | Paper Server JAR |

The combined pack build requires FFmpeg and JDK 25. It verifies the reviewed GeneralUser GS SoundFont checksum, renders and peak-normalizes 751 physical samples, uses Vorbis quality 4, decodes each OGG to reject inaudible output, enforces a 50,000,000-byte ceiling, and does not include the source `.sf2` in the ZIP. Release automation injects the final pack asset URL and SHA-1 into both `config.yml` and the JAR-only `enb-release-pack.properties` before generating `SHA256SUMS.txt`; the latter prevents stale server configs from shadowing an official upgrade.

## Branches and releases

| Ref | Role |
| --- | --- |
| `port/26.2` | Main development branch |
| `main` | Repository landing branch, synchronized at agreed checkpoints |
| `release/26.2` | Release maintenance branch, synchronized at agreed checkpoints |
| `v<mod-version>-mc26.2` | Exact source commit for a published release |

The current [workflow](../.github/workflows/build-26.2.yml) builds pushes to `port/26.2` and `release/26.2`, and pull requests targeting `main`. Its release job runs only for a push to `port/26.2` whose head commit message starts with `release:` and whose build jobs succeed.

Use `docs:` / `chore:` commits for repository maintenance. Branches may advance after a release; keep the published release tag anchored to the source commit that produced its artifacts. Full / Client / Server Resources use `mod_version`, while Paper Server has its own version in `bridge/build.gradle`.

## Validation boundaries

CI checks the configured tests, Full runtime content, Paper Client registry safety and packaged resources, plugin source injection, and plugin runtime resources. A passing run does not establish in-game behavior on Purpur.

The outstanding gameplay checks and planned Paper features are tracked in [ROADMAP.md](ROADMAP.md). Keep the MIT license and upstream credits when moving or reusing source files.

## Paper Client startup regression test

After building and packaging the Paper Client, run `./gradlew runPaperClientSmoke`. On Linux this requires Xvfb and an OpenGL-capable driver (CI uses software rendering). The separate `src/paperClientSmoke/` test mod is never packaged in release JARs. The task starts the actual Paper Client JAR with Fabric, waits for resource loading, checks the built-in item pack, opens the note editor, verifies vanilla Block/Item registries and checks MIDI 0–127 pitch factors plus vanilla attenuation/pitch behavior. It does not connect to a Paper server or verify audible output.

`python3 -m unittest discover -s scripts -p 'test_*.py' -v` tests the Mixin package guard. `./gradlew -p bridge test` runs the server payload validation tests. The release workflow requires these checks and the startup success marker, and publishes the matching `docs/releases/<mod_version>.md` file as release notes.
