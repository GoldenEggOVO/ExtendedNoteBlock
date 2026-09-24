# Extended Note Block · Minecraft 26.2

[English](../README.md) · **简体中文**

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Fabric Loader](https://img.shields.io/badge/Fabric_Loader-0.19.5-DBD0B4?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)](https://adoptium.net/)
[![Release](https://img.shields.io/badge/Release-2.13.1-4C8BF5?style=flat-square)](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.13.1-mc26.2)
[![License](https://img.shields.io/github/license/GoldenEggOVO/ExtendedNoteBlock?style=flat-square)](../LICENSE)

![Extended Note Block Banner](assets/ENB-Banner.webp)

Extended Note Block 为 Minecraft 带来完整 MIDI 音域的扩展音符盒、指挥棒、无线红石和 NBS 音乐工坊。它支持 MIDI、NBS 与常见音频导入，也可以将歌曲导出为 Minecraft 音乐结构。

当前正式版为 **Full Fabric / Paper Client 2.13.1**、**Paper Server 0.14.1**，适用于 **Minecraft 26.2 / Java 25**。

[下载 2.13.1](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.13.1-mc26.2) · [安装指南](INSTALLATION.md) · [功能展示](FEATURES.md) · [全部文档](README.md)

## 我应该安装哪个版本？

| 使用场景 | 需要安装 |
| --- | --- |
| 单人游戏 | 客户端安装 **Full Fabric** 与 Fabric API |
| Fabric 多人服务器 | 服务端和客户端都安装 **Full Fabric** 与 Fabric API |
| Paper / Purpur 服主 | 服务端安装 **Paper Server + CraftEngine 26.8.2**，并导入 ENB 注册资源 |
| Paper / Purpur 普通玩家 | 原版客户端即可；进服后加载服务器资源包 |
| Paper / Purpur 创作者 | 客户端安装 **Paper Client** 与 Fabric API，服务器安装 **Paper Server + CraftEngine** |

> [!IMPORTANT]
> **CraftEngine 是 Paper Server 的必需插件；Full Fabric 不需要 CraftEngine。**
>
> **Full Fabric 与 Paper Client 不能同时安装在同一个客户端。** Full Fabric 会注册真正的 ENB 方块；Paper Client 则保持原版 Registry，用于连接 Paper / Purpur。

## 主要功能

- MIDI **0–127**、128 种 GM 乐器、力度、延音、延迟、淡入淡出与 Pitch Cents。
- 128 键钢琴式编辑界面，以及可扩展的音量、弯音和声源位置控制。
- NBS / MIDI / WAV / MP3 / OGG / AIFF / AU 导入、试听和结构规划。
- Litematic、结构 NBT 与数据包导出；Paper Client 配合 Litematica 保存建筑参数，粘贴建筑或工坊投影后自动导入 ENB 数据。
- 指挥棒批量编辑、无线红石和 NBS Projection Receiver。
- Paper 服无 Mod 聆听：自动资源包用 32 种代表音色、751 个实际 OGG 覆盖完整音域。

[查看界面截图和功能说明](FEATURES.md)

## Paper / Purpur 模式

Paper Server 使用 **CraftEngine 26.8.2** 注册 ENB 方块与物品；ENB 保存完整音乐参数和投影时间轴。安装时必须同时部署 Release 中的 ENB CraftEngine 注册资源。

- **原版玩家：** 只加载 CraftEngine 生成的完整资源包，即可看到 ENB 方块并听到音乐；不再下发独立 ENB 包。安装时按指南关闭 CraftEngine 的重复自动发送。
- **Paper Client 玩家：** 获得编辑界面、完整 128 种音色和高级声音控制。
- **建筑复制：** 客户端安装 Litematica **0.28.8** 和 MaLiLib **0.29.6**，普通保存会附带服务器 ENB 参数，完整粘贴后自动导入。音乐工坊导出的 Paper 投影也支持自动导入。
- **数据保留：** MIDI、乐器、力度、延音、延迟、淡入淡出、Pitch Cents 和接收器时间轴随结构保留。旋转、镜像和子区域位置用于转换坐标。

复制权限默认仅 OP。自动导入使用 Litematica 命令粘贴：选择 Replace All、关闭 changed-block-only，并在装有 Servux / LitematicaFolia 时关闭 `pasteUsingServux`。Easy Place 逐块搭建仍使用手动恢复入口。详细安装、升级与兼容边界见[安装指南](INSTALLATION.md)。

## 快速开始

### 单人 / Fabric 服务器

1. 安装 **Java 25、Fabric Loader 0.19.5、Fabric API 0.159.0+26.2**。
2. 将 **Full Fabric** JAR 放入 `mods/`；Fabric 多人服务器的服务端与客户端都需安装。
3. 启动游戏，默认按 **N** 打开音乐工坊。

### Paper / Purpur 服务器

1. 正常停服并备份数据，将 **Paper Server 0.14.1 + CraftEngine 26.8.2** 放入 `plugins/`，移出旧版 ENB JAR。
2. 将 Release 中的 **ENB CraftEngine ZIP** 解压到服务器根目录，确认资源位于 `plugins/CraftEngine/resources/enb/`。此 ZIP 是服务端安装资源，不是直接发给玩家的最终资源包。
3. 在 CraftEngine 配置的 `resource-pack.delivery` 下，将 `send-on-join` 和 `resend-on-upload` 都设为 `false`。由 CraftEngine 生成、托管完整包，ENB 统一发送并检测加载状态。
4. 正常重启，按[资源包配置说明](INSTALLATION.md#paper-服务器资源包)生成并上传完整包。OP 可用 `/enb pack status` 检查、`/enb give all` 获取物品。
5. 普通玩家加载服务器资源包即可；创作者在客户端安装 **Paper Client + Fabric API**，按 **N** 打开工坊、右键 ENB 音符盒编辑参数。

[完整安装与升级指南](INSTALLATION.md) · [English setup guide](../README.md#quick-start)

## 文档

| 入口 | 内容 |
| --- | --- |
| [English guide](../README.md) | English overview, installation, resource packs and Litematica |
| [文档中心](README.md) | 用户、服主、开发者与历史文章总入口 |
| [安装与使用](INSTALLATION.md) | 依赖、安装、命令、资源包、Litematic 与排错 |
| [功能展示](FEATURES.md) | GUI、指挥棒、无线红石与音色包截图 |
| [Paper 架构](ARCHITECTURE.md) | 原版载体、同步、声音、导入和持久化 |
| [开发指南](DEVELOPMENT_zh-cn.md) | 工具链、构建、测试、分支与发布流程 |
| [路线图](ROADMAP.md) | 已完成能力、实机验证和后续功能 |
| [版本记录](../CHANGELOG.md) | 版本级变更摘要与历史发布文章 |
| [参与贡献](../CONTRIBUTING.md) | 问题报告、开发流程与提交检查清单 |
| [安全策略](../SECURITY.md) | 私下报告安全问题与支持范围 |

## 来源与许可证

- 原项目与原作者：[Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock) — **Atemukesu**。
- 26.1.1 移植与音乐工坊扩展：[BianFuuuu/ExtendedNoteBlock](https://github.com/BianFuuuu/ExtendedNoteBlock) — **BF_skt**。
- Minecraft 26.2 / Paper-Purpur Bridge 维护：**GoldenEggOVO**。
- [原版详细手册](https://atemukesu.github.io/ExtendedNoteBlock/) 可供 Full Fabric 功能参考；Paper 的实际能力以本仓库文档为准。

本项目使用 [MIT License](../LICENSE)，并保留原作者版权声明。音频及其他依赖材料见[第三方声明](../THIRD_PARTY_NOTICES.md)。
