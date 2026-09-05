# Paper / Purpur 架构

[返回首页](../README.md) · [开发指南](DEVELOPMENT_zh-cn.md) · [当前待办](ROADMAP.md)

## 三个程序版本

| 版本 | Registry 与运行职责 |
| --- | --- |
| Full Fabric | 注册真实 `extendednoteblock:*` Block、Item、BlockEntity 与 Menu，包含完整 Fabric 内容 |
| Paper Client | Fabric 客户端伴侣，复用声音、资源与客户端工具；打包时排除 Full Registry 依赖 |
| Paper Server | Paper / Purpur 插件，以原版方块和物品为载体，负责逻辑身份、数据、播放与同步 |

Full Fabric 与 Paper Client 不应同时安装。Paper 的编辑界面可以复制 Full 的视觉和交互，但不能直接继承依赖 Full Menu Registry 的容器界面；保存使用独立 Bridge Payload。

## 原版载体与身份

| ENB 对象 | Paper 实际载体 | 物品 CustomModelData string |
| --- | --- | --- |
| Extended Note Block | `minecraft:note_block` | `extendednoteblock:extended_note_block` |
| Conductor Wand | `minecraft:blaze_rod` | `extendednoteblock:conductor_wand` |
| Global Redstone Transmitter | `minecraft:red_concrete` | `extendednoteblock:global_redstone_transmitter` |
| Global Redstone Receiver OFF | `minecraft:green_concrete` | `extendednoteblock:global_redstone_receiver` |
| Global Redstone Receiver ON | `minecraft:redstone_block` | 世界方块状态，不是另一种背包载体 |
| NBS Projection Receiver | `minecraft:purple_concrete` | `extendednoteblock:nbs_projection_receiver` |

ENB 物品通过 Bukkit PDC 的 `enb_type` 识别逻辑类型，CustomModelData 的 `strings[0]` 用于视觉选择。放置后的世界对象按坐标登记，并持久化至插件数据文件。

服务器仍然发送原版 ItemStack。Visuals 使用 `minecraft:select` / `minecraft:custom_model_data` 匹配 ENB 字符串；不匹配时明确回退到载体的原版模型。因此普通音符盒、烈焰棒和混凝土保持原版外观。

## 物品外观与世界方块外观

Paper Client 内置包与独立 Visuals ZIP 从相同资源生成。独立资源包只处理 ENB 标记物品，不能单独识别世界中的 ENB 坐标。

Paper Server 向 Paper Client 同步维度内已登记对象的坐标、类型、ON/OFF 状态和音高类别。客户端只替换这些坐标的 baked model：

- Extended Note Block 使用 `note % 12` 对应的 C / C# / D / … / B 模型，以及 powered `_on` 变体。
- Transmitter、Receiver、Projection Receiver 使用对应的 Full Fabric OFF/ON 模型。
- Receiver ON 在服务端仍是真实 `redstone_block`，提供原版强度 15 的红石输出。
- 非 ENB 管理坐标保持原版渲染。

登录、切换世界和频道注册时同步快照；对象变化时增量更新。

## 声音

Paper Server 保存 MIDI Note、Instrument、Velocity、Sustain、Delay、Fade In 与 Fade Out。高级 Bridge 声音协议还提供 pitch multiplier、pitch cents、音量 / 空间位置更新和 start / update / stop。

客户端从最近的采样音符计算 `2^(半音差 / 12)`。Paper Client 2.8.0 添加专用、Registry-safe 的 SoundEngine Mixin，对 `extendednoteblock` 声音绕过 Minecraft 的最终 pitch clamp，并保留 Full 版约 48 格的声音衰减行为。

该修复用于解决低音区被夹到同一音高的问题。编译与静态打包检查已通过；完整 MIDI 音域的实际声音仍需游戏内试听验证。没有 Paper Client 的玩家听最接近的原版 Note Block fallback。

## Litematic 导出与导入边界

| 输出模式 | Palette 内容 | 2.8.0 行为 |
| --- | --- | --- |
| Full Fabric | 真实 `extendednoteblock:*` 方块 | 使用 Full 方块与对应数据 |
| Paper Client | Note Block、红色混凝土、紫色混凝土 | 避免未知 Registry ID 被解析为空气 |

Paper Projection `.litematic` 根 NBT 额外保存 `ExtendedNoteBlockBridge`：相对坐标、MIDI、GM 乐器、力度、延音、延迟、pitch cents 与 Projection Timeline。

Litematica 放置原版载体不会让 Paper 自动知道这些 ENB 参数。未来导入需要读取元数据、应用结构的实际位置变换，并由服务端验证后登记到 `objects.yml`、`notes.yml` 和 `projections.yml`。**当前尚未实现自动导入，也尚未实现 Workshop → Receiver 直接上传。**

## 无线红石

Wireless bus 按 World / Dimension 独立。相邻 Transmitter 与 Projection Receiver 可以形成 dedicated route：上升沿启动，下降沿停止，持续高电平不会在歌曲结束后自动循环；重新指向其他接收器时处理旧路由停止与新路由启动。

目前没有完整的 chunk-ticket 系统。
