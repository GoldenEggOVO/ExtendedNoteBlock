# 安装与使用 · Minecraft 26.2

[返回首页](../README.md) · [Paper 架构](ARCHITECTURE.md) · [待办与验证](ROADMAP.md)

## 下载与依赖

从 [v2.8.0-mc26.2 Release](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.8.0-mc26.2) 下载所需文件。Release 同时提供 `SHA256SUMS.txt`。

| 文件 | 安装对象 |
| --- | --- |
| `ExtendedNoteBlock-Full-Fabric-2.8.0-mc26.2.jar` | 单人 / Fabric 服务端模式 |
| `ExtendedNoteBlock-Paper-Client-Fabric-2.8.0-mc26.2.jar` | Paper / Purpur 专用 Fabric 客户端 |
| `ExtendedNoteBlock-Paper-Server-0.8.1-mc26.2.jar` | Paper / Purpur 服务端 |
| `ExtendedNoteBlock-Visuals-2.8.0-mc26.2.zip` | 可选独立物品资源包 |

| 环境 | 本版本构建基线 |
| --- | --- |
| Minecraft | 26.2 |
| Java | 25 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.159.0+26.2 |
| 插件服务端 | Paper / Purpur 26.2 |

Fabric API 只用于 Fabric 客户端 / 服务端，不放入 Paper 的 `plugins/`。上述版本是当前构建基线，并非对其他版本兼容性的承诺。

## Full Fabric

1. 安装目标版本的 Fabric Loader、Fabric API 和 Java。
2. 将 Full Fabric JAR 放入所用游戏实例的 `mods/`。
3. Fabric 多人服务器也安装同版 Full Fabric 与 Fabric API。

Full Fabric 注册真正的 ENB 方块和物品。连接纯 Paper / Purpur 服务器时应使用 Paper Client。

## Paper / Purpur

1. 停服后，将 Paper Server JAR 放入 `plugins/`；更新时移出旧版同插件 JAR。
2. 启动服务器，让插件加载配置与持久化数据。
3. 客户端安装 Fabric Loader、Fabric API 和 Paper Client JAR；Full Fabric 与 Paper Client 二选一。
4. OP 在游戏内运行 `/enb give all`，放置 ENB 物品后右键测试编辑界面。

Paper Client 内置 Visuals，不必额外启用独立 ZIP。未装 Mod 的玩家可以正常进入；如果希望这些玩家看到 ENB 标记物品的外观，可让他们启用独立 Visuals。独立资源包无法识别服务器中每个已放置方块的逻辑身份，所以世界方块的 ENB 模型需要 Paper Client。

更新插件时保留插件数据目录。`objects.yml`、`notes.yml` 和 `projections.yml` 分别涉及对象登记、音符参数及投影数据，复制普通方块不会自动生成这些登记。

旧版背包物品可能缺少新的 CustomModelData 字符串；测试物品材质时可以重新执行 `/enb give all`。已登记的世界方块会在支持同步的客户端登录后收到坐标快照。

## 基本使用

- **N**：打开 NBS 音乐工坊，可在按键设置中修改。
- 右键 ENB 音符盒：编辑 MIDI Note、GM Instrument、Velocity、Sustain、Delay、Fade In 与 Fade Out。
- Paper 指挥棒左键设置 Pos1，右键设置 Pos2。

| Paper 命令 | 用途 |
| --- | --- |
| `/enb help` | 查看帮助 |
| `/enb give all` | 获取一套 ENB 物品 |
| `/enb wand info` | 查看选区 |
| `/enb wand clear` | 清除选区 |
| `/enb wand set <属性> <值>` | 批量修改选区内的 ENB 音符盒 |

可批量编辑属性：`note`、`instrument`、`velocity`、`sustain`、`delay`、`fadein`、`fadeout`。默认最大选区体积为 **262144**，由服务器配置控制；具体参数可用 TAB 补全和 `/enb help wand` 查看。

## 音乐工坊目录

下列路径相对于所用游戏实例的游戏目录；启动器隔离实例时，不一定是默认 `.minecraft`。

| 路径 | 内容 |
| --- | --- |
| `extendednoteblock/songs/` | 歌曲文件 |
| `schematics/extendednoteblock/` | 结构输出 |
| `extendednoteblock/datapacks/` | 数据包输出 |

默认 `SIX_OCTAVES` 是 Projection Planner 的音域适配策略，范围 **24–95**；`TWO_OCTAVES` 为 **54–78**。它们不改变 ENB 音符盒本身 **MIDI 0–127** 的输入范围。

## 常见问题

### 连接 Paper 时因未知 ENB Registry ID 断线

检查客户端是否误装 Full Fabric，或同时装了两个 ENB 客户端版本。Paper Client 使用原版注册表，不向 Paper 注册自定义方块 / 物品。

### 粘贴结构后是普通音符盒，没有原来的音乐参数

2.8.0 的 Paper Projection Litematic 已改用原版载体，并保存 `ExtendedNoteBlockBridge` 元数据。Litematica 粘贴普通载体后，服务器仍需要未来的 Bridge Import 协议恢复逻辑身份与参数；当前版本尚未实现自动恢复。

### MIDI 低音区听起来仍然相同

先确认客户端实际加载的是 2.8.0 Paper Client，再用同一乐器依次试听 **0 / 12 / 24 / 36 / 48 / 60 / 72 / 84 / 96 / 108 / 120**。低音区修复已经通过 CI，实际听感仍需游戏内验证；反馈时附上客户端模组版本、乐器及测试音符。
