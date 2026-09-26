# Extended Note Block · Minecraft 26.2

**English** · [简体中文](docs/README_zh-cn.md)

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Fabric Loader](https://img.shields.io/badge/Fabric_Loader-0.19.5-DBD0B4?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)](https://adoptium.net/)
[![Release](https://img.shields.io/badge/Release-2.13.2-4C8BF5?style=flat-square)](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.13.2-mc26.2)
[![License](https://img.shields.io/github/license/GoldenEggOVO/ExtendedNoteBlock?style=flat-square)](LICENSE)

![Extended Note Block Banner](docs/assets/ENB-Banner.webp)

Extended Note Block adds MIDI-range note blocks, a conductor wand, wireless redstone and a music workshop to Minecraft. Import NBS, MIDI or supported audio files, edit and preview music, and export Minecraft music structures.

**Minecraft 26.2 · Java 25 · Full Fabric / Paper Client 2.13.2 · Paper Server 0.14.2**

[Download 2.13.2](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.13.2-mc26.2) · [Choose an edition](#choose-an-edition) · [Quick start](#quick-start) · [Litematica](#copying-with-litematica) · [Development guide](docs/DEVELOPMENT.md)

## Choose an edition

| Your setup | What to install |
| --- | --- |
| Single-player | **Full Fabric** and Fabric API in the client's `mods/` folder |
| Fabric multiplayer | **Full Fabric** and Fabric API on both server and clients |
| Paper / Purpur server owner | **Paper Server + CraftEngine 26.8.2**, plus the ENB CraftEngine installation ZIP |
| Paper / Purpur listener | No client mod required; accept the server's resource pack |
| Paper / Purpur music creator | **Paper Client** and Fabric API on the client; the server needs Paper Server and CraftEngine |

> [!IMPORTANT]
> **CraftEngine is required for Paper Server.** The ENB CraftEngine ZIP contains registration data and assets, not the CraftEngine plugin itself. Obtain CraftEngine separately from its author.
>
> **Do not install Full Fabric and Paper Client together.** Use Paper Client to connect to Paper / Purpur. Full Fabric registers custom blocks and does not require CraftEngine.

Fabric installations use **Fabric Loader 0.19.5** and **Fabric API 0.159.0+26.2**. These are the tested build versions, not a promise of compatibility with other versions. Do not put Fabric API in Paper's `plugins/` folder.

## Features

- MIDI notes **0–127**, 128 GM instrument IDs, velocity, sustain, delay, fades and pitch adjustment.
- A piano-style editor and music workshop with NBS / MIDI / WAV / MP3 / OGG / AIFF / AU import.
- Litematic, structure NBT and datapack export.
- Conductor-wand batch editing, wireless redstone and projection playback.
- Paper Client integration with Litematica to preserve ENB music data when saving and pasting structures.

Paper Client provides the full instrument set and advanced sound controls. Vanilla listeners use a smaller bank of **32 representative timbres and 47 percussion sounds**, covering MIDI 0–127. The two playback modes are not identical.

## Quick start

### Single-player or Fabric server

1. Install Java 25, Fabric Loader and Fabric API for Minecraft 26.2.
2. Put `ExtendedNoteBlock-Full-Fabric-2.13.2-mc26.2.jar` in `mods/`. For Fabric multiplayer, install it and Fabric API on both sides.
3. Start the game. Press **N** to open the music workshop; the key can be changed in Controls.

### Paper / Purpur server

1. Stop the server normally. Back up `plugins/ExtendedNoteBlockBridge/` and `plugins/CraftEngine/`, then remove the old ENB plugin JAR.
2. Put `ExtendedNoteBlock-Paper-Server-0.14.2-mc26.2.jar` and **CraftEngine 26.8.2** in `plugins/`.
3. Extract `ExtendedNoteBlock-CraftEngine-2.13.2-mc26.2.zip` into the server root. Its files must end up in `plugins/CraftEngine/resources/enb/`. This is an installation bundle, not the final pack to send directly to players.
4. Configure the single resource-pack delivery flow below. On a first installation, start once to create CraftEngine's configuration, then stop before editing it.
5. Restart normally, generate and upload the complete CraftEngine pack using your configured host, then join and accept the pack. An operator can run `/enb pack status` and `/enb give all`.
6. Creators install `ExtendedNoteBlock-Paper-Client-Fabric-2.13.2-mc26.2.jar` with Fabric API on their client. Press **N** for the workshop and right-click a registered ENB note block to edit it.

### One resource pack

**CraftEngine generates and hosts the complete pack; ENB sends that same pack and tracks its load status.** It contains the block mappings, models, textures and listener audio. ENB no longer downloads a separate GitHub `Server-Resources` ZIP.

In the existing `plugins/CraftEngine/config.yml`, set these two delivery options while preserving your hosting configuration:

```yaml
resource-pack:
  delivery:
    send-on-join: false
    resend-on-upload: false
```

Do not replace the entire configuration file with this snippet. If either automatic sender remains enabled, ENB pauses its own delivery and reports the conflict to avoid duplicate requests.

Generate and upload the complete pack with CraftEngine. The download address must be reachable by players. ENB obtains the generated file and player download URL through CraftEngine, verifies that their SHA-1 values match, and sends the pack. No URL, hash or output path needs to be entered in ENB.

Keep a valid ZIP and the ENB resource paths; pack protection that breaks CRC checks or renames those paths can prevent validation. Old ENB `url`, `sha1`, `id`, `use-official-release` and `combined-file` settings are ignored. Preserve the existing ENB data folders when upgrading.

| Command | Purpose |
| --- | --- |
| `/enb help` | Show available commands |
| `/enb give all` | Get ENB items; operator permissions by default |
| `/enb pack status` | Check delivery and loading status |
| `/enb pack resend` | Request the complete pack again |
| `/enb pack test <MIDI 0-127> [instrument 0-127]` | Test listener audio directly |

A successful load enables vanilla MIDI listener playback. Until then, playback falls back to limited vanilla note-block sounds. With ENB's default `resource-pack.required: true`, declining the pack disconnects the player.

## Language

Full Fabric and Paper Client menus follow Minecraft's client language. English and Simplified Chinese are included; choose your language in Minecraft's Language settings.

Paper Server defaults to English. On first start it creates `plugins/ExtendedNoteBlockBridge/lang/en_us.yml` and `zh_cn.yml`. Edit the messages in those files, then set `language: en_us` or `language: zh_cn` in `plugins/ExtendedNoteBlockBridge/config.yml`. Run `/enb reload` to apply changes. CraftEngine item names are configured separately in `plugins/CraftEngine/resources/enb/configuration/enb.yml` and require a CraftEngine pack rebuild after editing.

## Copying with Litematica

Use **Paper Client 2.13.2**, **Paper Server 0.14.2**, **CraftEngine 26.8.2**, **Litematica 0.28.8** and **MaLiLib 0.29.6**. Litematica and MaLiLib are optional for players who do not use this feature.

1. Select an existing build and save it normally in Litematica. Wait for ENB's server-side data snapshot to finish saving with the schematic. Music-workshop Paper projections also contain ENB metadata.
2. Load the schematic and choose the destination, rotation and mirror settings.
3. Use **command paste** with `pasteReplaceBehavior` set to **All**, and disable changed-block-only mode. If the server has Servux / LitematicaFolia, set `pasteUsingServux` to **false**.
4. After the paste, wait for ENB's successful import message before testing playback.

Copy/import permission (`extendednoteblockbridge.import`) is operator-only by default. Target chunks must already be loaded. Music parameters and receiver timelines are preserved, with coordinates adjusted for placement, rotation, mirroring and subregions.

**Servux Direct Paste, Easy Place and independent WorldEdit pastes do not trigger automatic ENB import.** For workshop projections built with Easy Place, the workshop's **Restore ENB** action remains available. Old schematics without ENB metadata cannot recover music settings from appearance alone; save the original build again using the new client. Keep the original file because third-party editors may strip the extra metadata.

## More information

- [Development guide](docs/DEVELOPMENT.md): English build, test and `main` branch release workflow.
- [Contributing](CONTRIBUTING.md), [security policy](SECURITY.md), [license](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md).
- [Detailed installation reference](docs/INSTALLATION.md), [feature screenshots](docs/FEATURES.md), [release notes](docs/releases/2.13.2.md) and [validation roadmap](docs/ROADMAP.md): Chinese documentation.
- [Upstream manual](https://atemukesu.github.io/ExtendedNoteBlock/): reference for Full Fabric features; Paper-specific behavior is documented in this repository.

Automated build and startup checks do not replace multiplayer visual or listening tests. See the validation roadmap for outstanding in-game checks.

## Credits

Original project: [Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock), by **Atemukesu**. The 26.1.1 port and music-workshop extensions are from [BianFuuuu/ExtendedNoteBlock](https://github.com/BianFuuuu/ExtendedNoteBlock), by **BF_skt**. Minecraft 26.2 and Paper / Purpur integration are maintained by **GoldenEggOVO**.

Released under the [MIT License](LICENSE), retaining upstream copyright notices.
