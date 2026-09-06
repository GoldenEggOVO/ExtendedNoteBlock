# Extended Note Block · Minecraft 26.2

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Fabric Loader](https://img.shields.io/badge/Fabric_Loader-0.19.5-DBD0B4?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)](https://adoptium.net/)
[![Release](https://img.shields.io/badge/Release-2.12.0-4C8BF5?style=flat-square)](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.12.0-mc26.2)
[![License](https://img.shields.io/github/license/GoldenEggOVO/ExtendedNoteBlock?style=flat-square)](LICENSE)

![Extended Note Block Banner](docs/assets/ENB-Banner.png)

Extended Note Block 为 Minecraft 带来完整 MIDI 音域的扩展音符盒、指挥棒、无线红石和 NBS 音乐工坊。它支持 MIDI、NBS 与常见音频导入，也可以将歌曲导出为 Minecraft 音乐结构。

当前正式版为 **Full Fabric / Paper Client 2.12.0**、**Paper Server 0.12.0**，适用于 **Minecraft 26.2 / Java 25**。

[下载 2.12.0](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.12.0-mc26.2) · [安装指南](docs/INSTALLATION.md) · [功能展示](docs/FEATURES.md) · [全部文档](docs/README.md) · [日本語](docs/README_ja-jp.md)

## 我应该安装哪个版本？

| 使用场景 | 需要安装 |
| --- | --- |
| 单人游戏 | 客户端安装 **Full Fabric** 与 Fabric API |
| Fabric 多人服务器 | 服务端和客户端都安装 **Full Fabric** 与 Fabric API |
| Paper / Purpur 服主 | 服务端安装 **Paper Server** 插件 |
| Paper / Purpur 普通玩家 | 原版客户端即可；进服后加载服务器资源包 |
| Paper / Purpur 创作者 | 客户端安装 **Paper Client** 与 Fabric API，服务器安装 **Paper Server** |

> [!IMPORTANT]
> **Full Fabric 与 Paper Client 不能同时安装在同一个客户端。** Full Fabric 会注册真正的 ENB 方块；Paper Client 则保持原版 Registry，用于连接 Paper / Purpur。

## 主要功能

- MIDI **0–127**、128 种 GM 乐器、力度、延音、延迟、淡入淡出与 Pitch Cents。
- 128 键钢琴式编辑界面，以及可扩展的音量、弯音和声源位置控制。
- NBS / MIDI / WAV / MP3 / OGG / AIFF / AU 导入、试听和结构规划。
- Litematic、结构 NBT 与数据包导出；Paper 投影可在粘贴后恢复完整 ENB 参数。
- 指挥棒批量编辑、无线红石和 NBS Projection Receiver。
- Paper 服无 Mod 聆听：自动资源包用 32 种代表音色、751 个实际 OGG 覆盖完整音域。

[查看界面截图和功能说明](docs/FEATURES.md)

## Paper / Purpur 模式

Paper Server 使用音符盒、混凝土等原版方块作为载体，并在插件数据中保存 ENB 身份与音乐参数。

- **原版玩家：** 加载服务器资源包后可听音乐并看到 ENB 物品材质；世界方块保持真实的原版载体外观。
- **Paper Client 玩家：** 获得编辑界面、完整 128 种音色、按坐标显示的世界方块模型与高级声音控制。
- **性能策略：** 不发送假方块，也不创建 `BlockDisplay` / `ItemDisplay`，因此不会为外观产生额外实体负担。

Paper Server 默认强制下发 2.12.0 的物品 + 声音资源包。资源包为 **47,040,904 bytes**，不覆盖普通世界方块，常规实时变调控制在约 ±3 半音。详细设置与排错见[安装指南](docs/INSTALLATION.md#paper-服务器资源包)。

## 快速开始

1. 从 [Releases](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.12.0-mc26.2) 下载与你场景匹配的 JAR。
2. Fabric 文件放入 `mods/`；Paper Server 文件放入服务端 `plugins/`。
3. 完整重启游戏或服务器。Paper 服 OP 可执行 `/enb give all` 获取测试物品。
4. 默认按 **N** 打开 NBS 音乐工坊；右键已登记的 ENB 音符盒打开编辑界面。

完整依赖、升级流程、命令、目录和 Litematic 恢复步骤见[安装与使用](docs/INSTALLATION.md)。

## 文档

| 入口 | 内容 |
| --- | --- |
| [文档中心](docs/README.md) | 用户、服主、开发者与历史文章总入口 |
| [安装与使用](docs/INSTALLATION.md) | 依赖、安装、命令、资源包、Litematic 与排错 |
| [功能展示](docs/FEATURES.md) | GUI、指挥棒、无线红石与音色包截图 |
| [Paper 架构](docs/ARCHITECTURE.md) | 原版载体、同步、声音、导入和持久化 |
| [开发指南](docs/DEVELOPMENT_zh-cn.md) | 工具链、构建、测试、分支与发布流程 |
| [路线图](docs/ROADMAP.md) | 已完成能力、实机验证和后续功能 |
| [版本记录](CHANGELOG.md) | 版本级变更摘要与历史发布文章 |

## 来源与许可证

- 原项目与原作者：[Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock) — **Atemukesu**。
- 26.1.1 移植与音乐工坊扩展：[BianFuuuu/ExtendedNoteBlock](https://github.com/BianFuuuu/ExtendedNoteBlock) — **BF_skt**。
- Minecraft 26.2 / Paper-Purpur Bridge 维护：**GoldenEggOVO**。
- [原版详细手册](https://atemukesu.github.io/ExtendedNoteBlock/) 可供 Full Fabric 功能参考；Paper 的实际能力以本仓库文档为准。

本项目使用 [MIT License](LICENSE)，并保留原作者版权声明。
