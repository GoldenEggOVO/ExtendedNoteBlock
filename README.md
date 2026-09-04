# Extended Note Block

[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.5-DBD0B4?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)](https://adoptium.net/)
[![License](https://img.shields.io/github/license/GoldenEggOVO/ExtendedNoteBlock?style=flat-square)](LICENSE)

![Extended Note Block Banner](./docs/assets/ENB-Banner.png)

Extended Note Block 是一个面向 Minecraft Java Edition 的音乐模组与 Paper/Purpur Bridge 项目。它扩展了音符盒的音域、力度、延音和空间控制，并提供 NBS、MIDI、音频导入以及多种音乐结构导出方式。

本仓库 fork 自 [Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock)，并基于 [BianFuuuu/ExtendedNoteBlock](https://github.com/BianFuuuu/ExtendedNoteBlock) 的 26.1.1 移植与音乐工坊继续维护。原作者署名和 MIT 许可证均完整保留。

## Minecraft 26.2：三个程序版本 + 一个共享材质包

每个正式 Release 固定提供三种程序版本。**Full Fabric 与 Paper Client 不要同时安装在同一个客户端。**

| 文件 | 用途 | 安装位置 |
| --- | --- | --- |
| `ExtendedNoteBlock-Full-Fabric-*.jar` | 单人模式，或服务端也安装 Full Mod 的 Fabric 服务器 | 客户端 `mods/`；Fabric 服务端也安装同版 Full Mod |
| `ExtendedNoteBlock-Paper-Client-Fabric-*.jar` | 连接安装 ENB Bridge 的 Paper/Purpur 服务器 | 客户端 `mods/` |
| `ExtendedNoteBlock-Paper-Server-*.jar` | Paper/Purpur 服务端桥接插件 | 服务端 `plugins/` |
| `ExtendedNoteBlock-Visuals-*.zip` | 可选独立材质包 | 客户端 `resourcepacks/` |

### Full Fabric

完整内容版本，保留真正的 `extendednoteblock:*` 注册表：扩展音符盒、指挥棒、无线红石、NBS Projection Receiver、完整 Fabric GUI 与服务端逻辑都在里面。

适合：

- 单人世界；
- Fabric 服务器，并且服务器也安装相同版本的 Full Fabric Mod。

**不要把 Full Fabric 当成纯 Paper/Purpur 的客户端伴侣。** Paper 不认识 Full 版自定义物品注册表，创造模式物品同步可能导致服务器解码失败。

### Paper Client

Paper/Purpur 专用 Fabric 客户端。它刻意不注册 Full 版的自定义 Block / Item / BlockEntity / Menu，因此保持 Paper 原版注册表安全。

Paper Client 与 Full Fabric 尽量共享同一套客户端能力：

- ExtendedNoteBlock 声音引擎与默认音色包；
- 音色包 GUI；
- NBS 音乐工坊；
- `.nbs` / `.mid` / `.midi` / WAV / MP3 / OGG / AIFF / AU 导入；
- 本地试听、投影预览；
- 原版红石、铁轨、Litematica、结构 `.nbt`、数据包 ZIP 导出；
- Bridge 高级声音与 pitch-cents 协议；
- Paper 专用音符盒编辑 GUI 协议。

Paper Client **内置与独立 `Visuals` ZIP 相同来源的 ENB 模型/纹理资源，并作为 always-enabled built-in resource pack 注册**，所以装 Paper Client 的玩家不需要再额外安装 Visuals ZIP。

### Paper/Purpur Server

Paper 服务端世界与背包始终只使用 `minecraft:*` 原版载体，逻辑身份由插件 PDC / `objects.yml` 保存。

| ENB 逻辑对象 | Paper 实际载体 |
| --- | --- |
| Extended Note Block | `minecraft:note_block` |
| Conductor Wand | `minecraft:blaze_rod` |
| Global Redstone Transmitter | `minecraft:red_concrete` |
| Global Redstone Receiver（OFF） | `minecraft:green_concrete` |
| Global Redstone Receiver（ON） | `minecraft:redstone_block` |
| NBS Projection Receiver | `minecraft:purple_concrete` |

没有安装任何 Mod 的玩家仍然可以正常进入服务器；扩展音符会自动回退到最接近的原版 Note Block 音高与音色。

## Shared Visuals：原 Mod 材质分离与内置

Release 额外提供：

`ExtendedNoteBlock-Visuals-*.zip`

它直接复用 Full Fabric 的原始 `extendednoteblock` 模型、纹理、物品模型与语言资源；Paper Client 内置的材质包也来自同一套资源源文件，因此两个客户端版本尽量保持一致。

### Paper 物品如何只改变 ENB 物品外观

Paper 插件给 `/enb give` 产生的 ENB 载体物品写入 Minecraft 26.2 自带的 `minecraft:item_model` 数据组件，例如：

- 标记的烈焰棒 -> `extendednoteblock:conductor_wand`
- 标记的音符盒 -> `extendednoteblock:extended_note_block`
- 标记的红/绿/紫混凝土 -> 对应 ENB 模型

因此：

- 有 Paper Client：内置材质自动提供这些模型；
- 没有 Mod、但手动启用 Visuals ZIP：标记的 ENB **物品**也可以使用原 Mod 外观；
- 什么都不装：仍然看到正常的原版载体；
- 普通烈焰棒、普通音符盒物品、普通混凝土物品不会被全局替换。

### 已放置方块的当前限制

纯原版 Resource Pack 无法读取 Paper PDC，也无法知道“某个坐标的绿色混凝土是 ENB Receiver、另一个只是普通绿色混凝土”。因此 Visuals ZIP **故意不全局覆盖**原版 Note Block / Concrete / Redstone Block 的 blockstate。

目前 Paper 世界里已经放置的 Bridge 方块仍使用原版方块外观。后续 Paper Client 会通过 ENB 对象位置同步 + 客户端定向渲染，只对真正的 Bridge 对象显示 Full Fabric 方块外观，而不影响普通原版方块。

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

- MIDI 0-127 全音域；
- 力度、延音、播放延迟、淡入、淡出；
- Full Fabric 支持音量曲线、弯音曲线和表达式声源移动；
- Paper Bridge 支持扩展播放、pitch-cents 投影与无 Mod 原版 fallback。

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
- Dedicated Projection Transmitter 使用上升沿触发，持续高电平不会让歌曲播完后重复启动。

### NBS Projection

- 每个 Projection Receiver 保存独立时间轴；
- 支持 MIDI Note、GM Instrument、Velocity、Sustain、pitch cents；
- Paper Client 玩家听完整扩展音色；
- 未装客户端 Mod 的玩家听最接近的原版 fallback。

### NBS 音乐工坊

默认按 `N` 打开 Paper Client / Full Fabric 的 NBS 音乐工坊。

- 直接读取 `.nbs`；
- 导入 `.mid` / `.midi`，保留速度、力度、声像与 GM 乐器信息；
- WAV、MP3、OGG、AIFF/AIF、AU 音频分析并转换为 NBS；
- 本地试听与投影布局预览；
- 速度、移调、力度、延音、音域、自定义音色等设置。

### 原版音乐导出

- 红石线路；
- 矿车 / 探测铁轨 / 动力铁轨线路；
- Litematica `.litematic`；
- 原版结构 `.nbt`；
- 数据包 ZIP；
- 支持 NBS Tempo Changer、开头留白、MIDI GM 映射与大型和弦。

## Paper/Purpur 快速安装

1. 服务端 `plugins/` 放 `ExtendedNoteBlock-Paper-Server-*.jar`。
2. 需要完整 ENB 音色和客户端工具的玩家，在 Fabric 26.2 客户端 `mods/` 放 `ExtendedNoteBlock-Paper-Client-Fabric-*.jar`。
3. Paper Client 已内置 Visuals，不需要再装独立 ZIP。
4. 不装 Paper Client 的玩家也可以直接进入服务器；如果只想要 ENB 物品模型，可单独启用 `ExtendedNoteBlock-Visuals-*.zip`。

OP：

```text
/enb give all
```

## Full Fabric 快速安装

单人模式：

1. Fabric Loader 26.2 + Fabric API；
2. 只把 `ExtendedNoteBlock-Full-Fabric-*.jar` 放入 `mods/`；
3. 不需要 Paper Server 插件。

Fabric 多人服务器则要求服务端也安装对应 Full Fabric Mod。

## 音乐工坊目录

歌曲：

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

## 构建与发布检查

GitHub Actions 发布前会验证：

- Full Fabric 真实包含完整模组入口与自定义物品；
- Paper Client 不泄漏自定义 Block / Item / Server Registry 类；
- Paper Client JAR 内存在 built-in Visuals pack；
- 独立 Visuals ZIP 不包含 Java class，也不全局覆盖 `minecraft:*` 载体 blockstate/item 定义；
- Paper Server 能在 Java 25 / Paper 26.2 API 下编译，并包含 `plugin.yml` / `config.yml`；
- Paper Server 标记物品会写入 `minecraft:item_model` 对应 ENB 模型。

## 项目来源与作者

- 原项目与原作者：[Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock) — **Atemukesu**
- 26.1.1 移植与音乐工坊扩展：[BianFuuuu/ExtendedNoteBlock](https://github.com/BianFuuuu/ExtendedNoteBlock) — **BF_skt**
- Minecraft 26.2 / Paper-Purpur Bridge 维护：**GoldenEggOVO**
- 原版详细手册：[atemukesu.github.io/ExtendedNoteBlock](https://atemukesu.github.io/ExtendedNoteBlock/)

## 许可证

本项目继续使用 [MIT License](LICENSE)。原作者版权声明保留。
