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
- 与 Full Fabric 对齐的 Extended Note Block 主编辑界面：GM 乐器下拉、力度、延音、延迟、Fade 与可滚动 128 键 MIDI 钢琴；
- Full Fabric 同款扩展 pitch 路径，可正常覆盖扩展多八度 MIDI 音域，不再把低音区夹成同一音高；
- 音色包 GUI；
- NBS 音乐工坊；
- `.nbs` / `.mid` / `.midi` / WAV / MP3 / OGG / AIFF / AU 导入；
- 本地试听、投影预览；
- 原版红石、铁轨、Litematica、结构 `.nbt`、数据包 ZIP 导出；
- Paper Client 的 Projection `.litematic` 使用原版 Note Block / Concrete 载体，因此未知 `extendednoteblock:*` 方块不会再解析成空气；Full Fabric 仍导出真正的 ENB 方块；
- Paper-safe Litematic 额外保留 `ExtendedNoteBlockBridge` NBT 元数据，记录 MIDI、GM 乐器、力度、延音、延迟、pitch-cents 与音符坐标，供后续 Bridge 导入/恢复逻辑使用；
- Bridge 高级声音与 pitch-cents 协议；
- Paper 专用音符盒编辑 GUI 协议；
- **按服务器同步坐标渲染已放置的 ENB 方块，复用 Full Fabric 原模型与 ON/OFF 状态。**

> Paper-safe `.litematic` 在 2.8.0 首先解决“ENB 位置变空气”的结构问题。把已经粘贴的原版载体自动恢复成 Paper 插件管理的 ENB 对象仍属于后续 Bridge Import 功能，2.8.0 不把它当作已完成能力。

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
