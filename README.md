# Extended Note Block · Minecraft 26.2

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Fabric Loader](https://img.shields.io/badge/Fabric_Loader-0.19.5-DBD0B4?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/GoldenEggOVO/ExtendedNoteBlock?style=flat-square)](LICENSE)

![Extended Note Block Banner](docs/assets/ENB-Banner.png)

Extended Note Block 为 Minecraft 提供扩展音符盒、指挥棒、无线红石和 NBS 音乐工坊，支持 MIDI、NBS 与常见音频导入、试听和音乐结构导出。

**当前正式版：Full Fabric / Paper Client 2.8.1，Paper Server 0.8.2，目标 Minecraft 26.2。**

[下载正式版](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.8.1-mc26.2) · [2.8.1 更新说明](docs/releases/2.8.1.md) · [安装指南](docs/INSTALLATION.md) · [日本語](docs/README_ja-jp.md)

## 选择安装版本

**Full Fabric 与 Paper Client 不能同时安装在同一个客户端。**

| 版本 | 用途 | 安装位置 |
| --- | --- | --- |
| **Full Fabric** | 单人模式，或服务端也安装 Full Mod 的 Fabric 服务器 | 客户端 `mods/`；Fabric 服务端也安装同版 Full Mod |
| **Paper Client** | 连接安装 ENB Bridge 的 Paper / Purpur 服务器 | Fabric 客户端 `mods/` |
| **Paper Server** | Paper / Purpur 服务端桥接插件 | 服务端 `plugins/` |
| **Visuals** | 可选独立资源包，提供 ENB 标记物品的外观 | 客户端 `resourcepacks/` |

Paper Client 已经内置同源 Visuals 资源，通常不需要再装独立 ZIP。没有客户端 Mod 的玩家也能进入 Paper 服务器，并听到最接近的原版音符盒回退声音。

## 快速安装

### 单人模式 / Fabric 服务器

1. 准备 Minecraft **26.2**、Java **25**、Fabric Loader **0.19.5** 和 Fabric API **0.159.0+26.2**。
2. 将 `ExtendedNoteBlock-Full-Fabric-2.8.1-mc26.2.jar` 放入客户端 `mods/`。
3. Fabric 多人服务器还需安装同版 Full Fabric 和 Fabric API。

### Paper / Purpur 服务器

1. 将 `ExtendedNoteBlock-Paper-Server-0.8.2-mc26.2.jar` 放入服务器 `plugins/`，重启服务器。
2. 需要完整音色、编辑界面和世界方块外观的玩家，在 Fabric 26.2 客户端安装 Fabric API 与 `ExtendedNoteBlock-Paper-Client-Fabric-2.8.1-mc26.2.jar`。
3. OP 在游戏内运行 `/enb give all` 获取测试物品。

默认按 **N** 打开 NBS 音乐工坊；右键已登记的 ENB 音符盒打开编辑界面。升级、目录和常见问题见[安装指南](docs/INSTALLATION.md)。

## 功能与当前边界

| 功能 | Full Fabric | Paper Client + Paper Server |
| --- | --- | --- |
| MIDI 0–127、乐器、力度、延音、延迟、淡入淡出 | 支持 | 支持；SoundEngine 全音域检查已加入，实际听感待验证 |
| GM 乐器下拉与 128 键钢琴编辑 | 支持 | 已移植基础界面 |
| 音量曲线、弯音曲线、表达式声源移动编辑 | 支持 | 完整编辑界面待移植 |
| 指挥棒区域批量编辑 | GUI 与区域可视化 | 已有选区和命令；GUI 与可视化待移植 |
| 无线红石与 Projection Receiver | 支持 | 支持；接收器开启时是真实 15 级红石源 |
| NBS / MIDI / 音频导入与本地试听 | 支持 | 支持 |
| Litematic、结构 NBT、数据包导出 | 支持 | 支持；Projection Litematic 使用原版载体 |
| 粘贴后恢复 ENB 参数 | 使用真实 ENB 方块数据 | 元数据已保留，自动导入尚未实现 |

音乐工坊支持 `.nbs`、`.mid`、`.midi`、WAV、MP3、OGG、AIFF / AIF 和 AU。音频分析属于音高检测转换，不保证完整还原原曲的全部声部。

**2.8.1 修复 Paper Client 启动崩溃，并增加打包后真实客户端启动、GUI 和音高检查；这些结果不等于 Purpur 多人游戏内验证。** Paper-safe Litematic 解决了未知方块变空气的问题，粘贴后的原版载体还不会自动恢复为可播放的 ENB 对象。

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [安装与使用](docs/INSTALLATION.md) | 下载文件、安装步骤、游戏目录、基础命令与排错 |
| [Paper 架构](docs/ARCHITECTURE.md) | 原版载体、PDC、物品材质、坐标渲染、声音与导出边界 |
| [开发指南（中文）](docs/DEVELOPMENT_zh-cn.md) / [English](docs/DEVELOPMENT.md) | Java / Gradle 环境、源码准备脚本、三种产物构建与分支规则 |
| [待办与验证](docs/ROADMAP.md) | 游戏内验证项与 Full Fabric → Paper 功能对齐 |
| [更新日志](CHANGELOG.md) | 当前版本与历史变更记录 |
| [历史源码归档](legacy/README.md) | 不参与 26.2 构建的旧版本源码和工具 |

## 来源与许可证

- 原项目与原作者：[Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock) — **Atemukesu**。
- 26.1.1 移植与音乐工坊扩展：[BianFuuuu/ExtendedNoteBlock](https://github.com/BianFuuuu/ExtendedNoteBlock) — **BF_skt**。
- Minecraft 26.2 / Paper-Purpur Bridge 维护：**GoldenEggOVO**。
- [原版详细手册](https://atemukesu.github.io/ExtendedNoteBlock/) 可供 Full Fabric 功能参考；Paper 的可用范围以本仓库文档为准。

本项目继续使用 [MIT License](LICENSE)，保留原作者版权声明。
