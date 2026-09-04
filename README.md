# Extended Note Block

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.5-DBD0B4?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/GoldenEggOVO/ExtendedNoteBlock?style=flat-square)](LICENSE)

![Extended Note Block Banner](./docs/assets/ENB-Banner.png)

Extended Note Block 是一个面向 Minecraft Java Edition 的音乐模组与 Paper/Purpur Bridge 项目。它扩展了音符盒的音域、力度、延音和空间控制，并提供 NBS、MIDI、音频导入以及多种音乐结构导出方式。

本仓库 fork 自 [Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock)，并基于 [BianFuuuu/ExtendedNoteBlock](https://github.com/BianFuuuu/ExtendedNoteBlock) 的 26.1.1 移植与音乐工坊继续维护。原作者署名和 MIT 许可证均完整保留。

## Minecraft 26.2：三个程序版本

同一个 Release 现在固定提供三种程序版本，请按你的服务器类型选择，不要把 Full Fabric 和 Paper Client 同时放入同一个客户端。

| 文件 | 用途 | 安装位置 |
| --- | --- | --- |
| `ExtendedNoteBlock-Full-Fabric-*.jar` | 单人模式、或服务端也安装完整 Fabric Mod 的 Fabric 服务器 | 客户端 `mods/`，Fabric 服务端也需要对应完整 Mod |
| `ExtendedNoteBlock-PaperClient-Fabric-*.jar` | 连接安装 ENB Bridge 的 Paper/Purpur 服务器 | 客户端 `mods/` |
| `ExtendedNoteBlock-PaperPlugin-*.jar` | Paper/Purpur 服务端桥接插件 | 服务端 `plugins/` |

### Full Fabric

完整内容版本，保留真正的 `extendednoteblock:*` 自定义注册表内容，包括扩展音符盒、指挥棒、无线红石方块、NBS Projection Receiver、完整 GUI 与 Fabric 服务端逻辑。

它适合：

- 单人世界；
- Fabric 服务端，并且服务端也安装相同的 Full Fabric Mod。

**不要用 Full Fabric JAR 作为纯 Paper/Purpur 的客户端伴侣。** Paper 不认识 Full 版的自定义物品注册表，创造模式物品同步可能因此断开连接。

### Paper Client

Paper/Purpur 专用 Fabric 客户端版本。它刻意不注册自定义 Block / Item / BlockEntity / Menu 注册表，因此可以安全连接原版注册表的 Paper/Purpur。

Paper Client 与 Full Fabric 尽量共享相同的客户端实现：

- ExtendedNoteBlock 声音引擎与默认音色包；
- 音色包 GUI；
- NBS 音乐工坊；
- `.nbs` / `.mid` / `.midi` / 音频导入；
- 本地试听与投影预览；
- 原版红石、铁轨、Litematica、结构方块、数据包导出；
- Bridge 高级声音与 pitch-cents 协议；
- 后续 Paper 专用编辑 GUI。

### Paper Plugin

Paper/Purpur 服务端只保存和运行逻辑数据，世界与背包中只使用 `minecraft:*` 原版载体。没有安装客户端 Mod 的玩家也可以正常进入服务器，并会听到最接近的原版音符盒 fallback。

当前载体：

| ENB 逻辑对象 | Paper 实际载体 |
| --- | --- |
| Extended Note Block | `minecraft:note_block` |
| Conductor Wand | `minecraft:blaze_rod` |
| Global Redstone Transmitter | `minecraft:red_concrete` |
| Global Redstone Receiver（OFF） | `minecraft:green_concrete` |
| Global Redstone Receiver（ON） | `minecraft:redstone_block` |
| NBS Projection Receiver | `minecraft:purple_concrete` |

逻辑身份通过插件 PDC 与 `objects.yml` 保存，而不是通过自定义物品 ID 保存。

## Shared Visual Compatibility Pack

Release 还提供：

`ExtendedNoteBlock-Visuals-*.zip`

这是一个 Minecraft 26.2 Resource Pack，直接复用 Full Fabric 原来的 ExtendedNoteBlock 模型和纹理，让 Paper Client 上的原版载体尽可能接近 Full Fabric 的外观。

把 ZIP 原样放到：

```text
.minecraft/resourcepacks/
```

然后在游戏资源包界面启用即可。

### 当前视觉包限制

纯原版 Resource Pack 无法读取 Paper 插件写入的 PDC / `enb_type`，因此它目前无法区分：

- “这个红色混凝土是 ENB Transmitter”；
- “旁边那个红色混凝土只是普通方块”。

所以 **启用 Visuals Pack 的客户端会全局替换这些载体的外观**。不启用材质包的玩家仍然看到完全正常的原版方块和物品。

后续 Paper Client 会增加 ENB 对象位置同步与客户端定向渲染，届时可以只给真正的 ENB Bridge 对象显示 Full Fabric 外观，而不影响普通原版载体。

## 运行环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 或更高 |
| Fabric API | 0.159.0+26.2 或更高 |
| Java | 25 或更高 |
| Paper/Purpur Bridge | Minecraft 26.2 |

## 主要功能

### 扩展音符盒

- MIDI 0-127 全音域，不再受原版两八度限制。
- 可设置力度、延音、播放延迟、淡入与淡出。
- Full Fabric 支持音量曲线、弯音曲线和基于表达式的声源移动。
- Paper Bridge 已实现基础扩展播放、pitch-cents 投影播放与无 Mod 原版 fallback。

### 指挥棒

Paper Bridge 中指挥棒使用带 ENB 标记的烈焰棒：

- 左键方块设置 Pos1；
- 右键方块设置 Pos2；
- `/enb wand info` 查看选区；
- `/enb wand clear` 清除选区；
- `/enb wand set <note|instrument|velocity|sustain|delay|fadein|fadeout> <value>` 批量修改选区内扩展音符盒。

### 全局无线红石

- Paper Transmitter 读取真实邻接红石输入；
- Receiver 激活时提供真实 15 级原版红石输出；
- Dedicated Projection Transmitter 使用上升沿触发，不会在持续高电平时重复重播完整歌曲。

### NBS Projection

- 每个 Projection Receiver 保存独立时间轴；
- 支持 MIDI Note、GM Instrument、Velocity、Sustain 与 pitch cents；
- 装 Paper Client 的玩家听扩展音色；
- 未装客户端 Mod 的玩家听最接近的原版 fallback。

### NBS 音乐工坊

默认按 `N` 打开 Paper Client / Full Fabric 的 NBS 音乐工坊。

- 直接读取 `.nbs`；
- 导入 `.mid` 和 `.midi`，保留速度、力度、声像与 GM 乐器信息；
- 导入 WAV、MP3、OGG、AIFF/AIF、AU 并分析音高转换为 NBS；
- 本地试听；
- 投影布局预览；
- 速度、移调、力度、延音、音域、自定义音色等设置。

### 原版音乐导出

- 红石线路；
- 矿车 / 探测铁轨 / 动力铁轨线路；
- Litematica `.litematic`；
- 原版结构方块 `.nbt`；
- 数据包 ZIP；
- NBS Tempo Changer、开头留白、MIDI GM 映射、大型和弦等。

## Paper/Purpur 快速安装

1. 服务端 `plugins/` 放入 `ExtendedNoteBlock-PaperPlugin-*.jar`。
2. 需要完整 ENB 音色与客户端工具的玩家，在 Fabric 26.2 客户端 `mods/` 放入 `ExtendedNoteBlock-PaperClient-Fabric-*.jar`。
3. 想让 Paper 载体看起来接近 Full Fabric 的玩家，把 `ExtendedNoteBlock-Visuals-*.zip` 放入 `resourcepacks/` 并启用。
4. 不想安装任何客户端内容的玩家可以直接进入服务器，无需额外 Mod。

OP 可以使用：

```text
/enb give all
```

获取所有 Paper Bridge 载体。

## Full Fabric 快速安装

单人模式：

1. 安装 Fabric Loader 26.2 与 Fabric API。
2. 只把 `ExtendedNoteBlock-Full-Fabric-*.jar` 放入 `mods/`。
3. 不需要 Paper Plugin。

Fabric 多人服务器则要求服务端也安装对应 Full Fabric Mod。

## 音乐工坊目录

歌曲目录：

```text
.minecraft/extendednoteblock/songs/
```

结构输出：

```text
.minecraft/schematics/extendednoteblock/
```

数据包输出：

```text
.minecraft/extendednoteblock/datapacks/
```

## 从源码构建

Windows：

```powershell
.\gradlew.bat clean test build
```

Linux / macOS：

```bash
./gradlew clean test build
```

Full Fabric 构建位于 `build/libs/`。Paper Client 与 Visuals Pack 由 `scripts/` 中的安全打包脚本生成，Paper Plugin 位于 `bridge/` 子工程。

GitHub Actions 发布前会验证：

- Full Fabric 真实包含完整模组入口与自定义物品；
- Paper Client 不泄漏自定义 Block / Item / Server Registry 类；
- Paper Plugin JAR 包含 `plugin.yml` 与 `config.yml`；
- Visuals Pack 不包含 Java class，并包含所有必要载体映射。

## 项目来源与作者

- 原项目与原作者：[Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock) — **Atemukesu**
- 26.1.1 移植与音乐工坊扩展：[BianFuuuu/ExtendedNoteBlock](https://github.com/BianFuuuu/ExtendedNoteBlock) — **BF_skt**
- Minecraft 26.2 / Paper-Purpur Bridge 维护：**GoldenEggOVO**
- 原版详细手册：[atemukesu.github.io/ExtendedNoteBlock](https://atemukesu.github.io/ExtendedNoteBlock/)

## 许可证

本项目继续使用 [MIT License](LICENSE)。原作者的版权声明未被删除；使用、修改或分发时请保留许可证与版权信息。
