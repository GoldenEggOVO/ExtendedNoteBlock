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

适合单人世界，或服务端也安装相同版本 Full Mod 的 Fabric 服务器。**不要把 Full Fabric 当成纯 Paper/Purpur 的客户端伴侣**，因为 Paper 不认识 Full 版自定义物品注册表。

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
- Paper 专用音符盒编辑 GUI 协议；
- **按服务器同步坐标渲染已放置的 ENB 方块，复用 Full Fabric 原模型与 ON/OFF 状态。**

Paper Client **内置与独立 `Visuals` ZIP 同源的 ENB 模型/纹理，并作为 always-enabled built-in resource pack 注册**，所以安装 Paper Client 后不需要再单独安装 Visuals ZIP。

### Paper/Purpur Server

Paper 服务端世界与背包始终只使用 `minecraft:*` 原版载体。插件使用两层元数据：

- Bukkit PDC `enb_type`：服务器逻辑身份；
- `minecraft:custom_model_data` 字符串：仅用于客户端物品材质选择。

| ENB 逻辑对象 | Paper 实际载体 | CustomModelData string |
| --- | --- | --- |
| Extended Note Block | `minecraft:note_block` | `extendednoteblock:extended_note_block` |
| Conductor Wand | `minecraft:blaze_rod` | `extendednoteblock:conductor_wand` |
| Global Redstone Transmitter | `minecraft:red_concrete` | `extendednoteblock:global_redstone_transmitter` |
| Global Redstone Receiver（OFF） | `minecraft:green_concrete` | `extendednoteblock:global_redstone_receiver` |
| Global Redstone Receiver（ON） | `minecraft:redstone_block` | 世界方块状态，不是背包物品载体 |
| NBS Projection Receiver | `minecraft:purple_concrete` | `extendednoteblock:nbs_projection_receiver` |

没有安装任何 Mod / Resource Pack 的玩家仍然可以正常进入服务器；他们看到的就是普通原版载体，扩展音符会自动回退到最接近的原版 Note Block 音高与音色。

## Shared Visuals：Hypixel 风格的原版载体 + CustomModelData

Release 额外提供 `ExtendedNoteBlock-Visuals-*.zip`。它直接复用 Full Fabric 的原始 ENB 模型、纹理、物品模型与语言资源；Paper Client 内置包来自完全相同的资源源文件。

### 如何只改变 ENB 物品，而不影响普通原版物品

服务器仍然只发送原版 ItemStack，例如 Conductor Wand 的真实物品 ID 仍是：

```text
minecraft:blaze_rod
```

插件同时保留 PDC `enb_type=conductor_wand` 供服务器识别，并写入一条仅用于视觉选择的 CustomModelData 字符串：

```text
extendednoteblock:conductor_wand
```

Visuals Pack / Paper Client 内置包使用 Minecraft 26.2 的 `minecraft:select` + `minecraft:custom_model_data` 读取 `strings[0]`：

- 精确匹配 ENB 字符串 -> 使用对应 `extendednoteblock:*` 原 Mod 模型；
- 不匹配 / 没有 CustomModelData -> 明确 fallback 到该载体的原版模型。

所以最终效果是：

- 安装 Paper Client：ENB 标记物品自动显示原 Mod 外观；
- 不装 Mod、只启用 Visuals ZIP：ENB 标记物品同样显示原 Mod 外观；
- 什么都不装：看到正常的原版音符盒 / 烈焰棒 / 混凝土；
- 普通烈焰棒、普通音符盒、普通混凝土始终保持原版外观；
- Paper 服务器永远不需要注册 `extendednoteblock:*` 自定义物品 ID。

这就是 Paper 版采用的 Hypixel 风格思路：**原版载体 + 服务器元数据 + 客户端资源包模型选择**。

### 已放置方块渲染（Paper Client）

CustomModelData 只负责 ItemStack。物品放到世界后，Paper 服务端仍然只保存普通 `minecraft:*` Block；为了既显示 Full Fabric 外观、又不影响普通原版方块，Paper Client 使用了第二层“坐标感知渲染”。

Paper Server 会向支持该协议的 Paper Client 同步当前维度中由 Bridge 管理的：

- 方块坐标；
- ENB 逻辑类型；
- 当前 ON/OFF 状态；
- Extended Note Block 的 MIDI 音高 pitch class。

Paper Client 只包装可能作为载体的 Note Block / 红绿紫 Concrete / Redstone Block 的 baked model。渲染每个坐标时：

- 该坐标不是 ENB Bridge 对象 -> 原版模型完全不变；
- 该坐标是 ENB Bridge 对象 -> 输出对应 Full Fabric 模型。

当前四类已放置方块的视觉行为：

- Extended Note Block：根据 MIDI `note % 12` 使用 C / C# / D / ... / B 原模型，并根据红石状态切换 `_on` 模型；
- Global Redstone Transmitter：使用 Full Fabric Transmitter OFF/ON 模型；
- Global Redstone Receiver：使用 Full Fabric Receiver OFF/ON 模型。服务端 ON 时仍然是真正的 `minecraft:redstone_block`，因此红石输出保持 15，但 Paper Client 在该坐标显示 Receiver ON 外观；
- NBS Projection Receiver：使用 Full Fabric Projection Receiver OFF/ON 模型。

客户端进入服务器、切换世界或注册同步频道时会收到完整快照；后续放置、破坏、移除、音高修改与红石状态变化会增量刷新对应坐标的 chunk geometry。

**独立 Visuals ZIP 仍然只能负责物品外观。** 没有 Paper Client 的玩家无法知道服务器管理的世界坐标，因此已放置方块继续显示原版载体，这是预期的兼容回退行为。

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
- Paper Bridge 支持扩展播放、pitch-cents 投影与无 Mod 原版 fallback；
- Paper Client 右键已放置的 Extended Note Block 可打开独立编辑 GUI，并保存回 Paper 服务端。

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
2. 需要完整 ENB 音色、客户端工具和已放置方块 Full Fabric 外观的玩家，在 Fabric 26.2 客户端 `mods/` 放 `ExtendedNoteBlock-Paper-Client-Fabric-*.jar`。
3. Paper Client 已内置 Visuals，不需要再装独立 ZIP。
4. 不装 Paper Client 的玩家也可以直接进入服务器；如果只想要 ENB 标记物品的原 Mod 外观，可单独启用 `ExtendedNoteBlock-Visuals-*.zip`。

OP 可使用：

```text
/enb give all
```

> 从旧版 Paper Server 升级后，背包里已经存在的旧 ENB 载体可能没有新的 CustomModelData 字符串。测试物品材质时建议重新执行 `/enb give all` 获取一套新物品；已放置并仍记录在 `objects.yml` 中的 Bridge 方块不需要重新放置，Paper Client 登录后会收到同步快照。

## Full Fabric 快速安装

单人模式：安装 Fabric Loader 26.2 + Fabric API，然后只把 `ExtendedNoteBlock-Full-Fabric-*.jar` 放进 `mods/`。不需要 Paper Server 插件。

Fabric 多人服务器要求服务端也安装对应 Full Fabric Mod。

## 音乐工坊目录

歌曲：`.minecraft/extendednoteblock/songs/`

结构输出：`.minecraft/schematics/extendednoteblock/`

数据包输出：`.minecraft/extendednoteblock/datapacks/`

## 构建与发布检查

GitHub Actions 发布前会验证：

- Full Fabric 真实包含完整模组入口与自定义物品；
- Paper Client 不泄漏自定义 Block / Item / Server Registry 类；
- Paper Client JAR 内存在 always-enabled built-in Visuals pack；
- Paper Client 与独立 Visuals ZIP 都包含 CustomModelData string 选择器与明确的原版 fallback；
- Paper Client JAR 包含坐标缓存、动态 baked-model 包装器与四类 Full Fabric 方块模型资源；
- 独立 Visuals ZIP 不全局覆盖原版方块 blockstate；
- Paper Server 同时保留 PDC 逻辑身份与 CustomModelData 物品视觉身份，同时不发送自定义注册表 ItemStack；
- Paper Server 编译验证 `bridge_object_sync` 世界对象同步、右键编辑通道与运行资源；
- Paper Server JAR 包含 `plugin.yml` / `config.yml`。

## 项目来源与作者

- 原项目与原作者：[Atemukesu/ExtendedNoteBlock](https://github.com/atemukesu/ExtendedNoteBlock) — **Atemukesu**
- 26.1.1 移植与音乐工坊扩展：[BianFuuuu/ExtendedNoteBlock](https://github.com/BianFuuuu/ExtendedNoteBlock) — **BF_skt**
- Minecraft 26.2 / Paper-Purpur Bridge 维护：**GoldenEggOVO**
- 原版详细手册：[atemukesu.github.io/ExtendedNoteBlock](https://atemukesu.github.io/ExtendedNoteBlock/)

## 许可证

本项目继续使用 [MIT License](LICENSE)。原作者版权声明保留。
