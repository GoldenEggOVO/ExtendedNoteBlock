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

Paper Client 内置包、独立 Visuals ZIP 与自动下发的 Server Resources 复用同源视觉资源。资源包本身不能读取服务端 PDC 或坐标，因此 Paper Server 会对成功加载组合包、且没有安装 Paper Client 的玩家发送坐标级假方块状态；世界存档中的真实载体仍为 `minecraft:note_block`。

无 Mod 客户端的 ENB 使用两个预留的 Note Block 状态：OFF 模型六面统一引用 `a_top.png`，ON 模型六面统一引用 `a_top_on.png` 并以模型级 15 亮度渲染。组合包完整列出 Note Block 的 1350 个状态，除这两个预留状态外全部显式回退 `minecraft:block/note_block`。这不会生成 `BlockDisplay`、改变世界方块或占用红石灯状态；模型满亮也不会在世界中产生真实光照。原版资源包的状态覆盖是全局的，因此普通音符盒若极少见地自然落在同一个 `custom_head + note 24` 状态，也会显示该模型，但其数据和逻辑不变。

服务器按玩家已收到的区块维护 ENB 坐标索引，并用 multi-block change 按区块 section 批量刷新。资源包加载成功、区块发送、切换世界、ENB 放置 / 删除与通电状态变化都会同步；资源包撤销时恢复该玩家看到的真实方块状态。

Paper Server 向 Paper Client 同步维度内已登记对象的坐标、类型、ON/OFF 状态和音高类别。客户端只替换这些坐标的 baked model：

- Extended Note Block 使用 `note % 12` 对应的 C / C# / D / … / B 模型，以及 powered `_on` 变体。
- Transmitter、Receiver、Projection Receiver 使用对应的 Full Fabric OFF/ON 模型。
- Receiver ON 在服务端仍是真实 `redstone_block`，提供原版强度 15 的红石输出。
- 非 ENB 管理坐标保持原版渲染。

登录、切换世界和频道注册时同步快照；对象变化时增量更新。检测到 Paper Client 插件频道后，服务器不再下发（或移除已下发的）原版聆听组合包，并恢复任何客户端假状态；Mod 客户端继续看到原本的按音高模型和 `_on` 变体。

## 声音

Paper Server 保存 MIDI Note、Instrument、Velocity、Sustain、Delay、Fade In、Fade Out 与导入的 Pitch Cents。高级 Bridge 声音协议还提供 pitch multiplier、pitch cents、音量 / 空间位置更新和 start / update / stop。

Paper Client 从最近的采样音符计算 `2^(半音差 / 12)`。2.8.0 添加专用、Registry-safe 的 SoundEngine Mixin，对 `extendednoteblock` 声音绕过 Minecraft 的最终 pitch clamp；2.8.1 修复 Mixin 包冲突，并将 48 格衰减限定到 ENB 声音。

2.10.1 为没有 Paper Client 的玩家提供组合资源包。128 个 GM program 每四个映射到一种代表音色，共 32 种；每种预渲染 MIDI 0、12、…、120 共 11 个锚点，运行时只在原版允许的 0.5–2.0 倍范围内做小幅变调，因此覆盖 MIDI 0–127。另有 MIDI 35–81 共 47 个独立打击乐采样。每个事件提供 8 个逻辑别名，让服务端能独立停止常见的同音重叠而不复制 OGG。物理采样会做有上限的峰值归一化，构建时再解码检查，避免极端音区只有事件却几乎无声。

Paper Server 在玩家加入 40 ticks 后发送带固定 UUID、HTTPS URL 与 SHA-1 的资源包请求，并只在收到 `SUCCESSFULLY_LOADED` 后向该玩家发送 `extendednoteblock_listener:*` 声音。正式 URL / SHA-1 来自 JAR 内独立 metadata，默认不受旧磁盘配置覆盖；`/enb pack status|resend` 提供诊断和重发。加载中、拒绝或失败时保留 Note Block fallback；检测到 Paper Client 插件频道的玩家继续走 Bridge 声音协议，避免重复播放。原版模式不实时还原高级音量曲线、连续弯音或表达式声源移动。

## Litematic 导出与导入边界

| 输出模式 | Palette 内容 | 2.8.0 行为 |
| --- | --- | --- |
| Full Fabric | 真实 `extendednoteblock:*` 方块 | 使用 Full 方块与对应数据 |
| Paper Client | Note Block、红色混凝土、紫色混凝土 | 避免未知 Registry ID 被解析为空气 |

Paper Projection `.litematic` 根 NBT 额外保存 `ExtendedNoteBlockBridge`：相对坐标、MIDI、GM 乐器、力度、延音、延迟、pitch cents 与 Projection Timeline。

Litematica 放置原版载体不会让 Paper 自动知道这些 ENB 参数。2.9.0 的 Paper Client 提供「恢复 ENB」界面，以已粘贴的红色发射器为定位点，读取元数据并应用平移、先镜像后旋转的坐标变换。

客户端通过 `extendednoteblock:bridge_import` 上传 Begin / Batch / Finish / Cancel；服务端通过 `extendednoteblock:bridge_import_status` 回报接收、校验和持久化结果。协议仅含数字、坐标、UUID 与字符串，不引用自定义 Registry。每批最多 128 个音符，收到确认后再上传下一批；总数受客户端和服务端上限约束。

Paper Server 0.9.0 校验 OP / 导入权限、世界、玩家与定位点距离、世界边界、已加载区块、全部载体和坐标唯一性，在最终提交前重新核对目标。提交恢复发射器、接收器、音符配置和投影曲目，并分别写入 `objects.yml`、`notes.yml`、`projections.yml`；正常坐标同步随后刷新模型。每个 YAML 文件先写临时文件再替换，保存失败时明确报告数据已应用但未成功落盘。

2.8.x 元数据没有淡入 / 淡出字段时沿用导出器原值 0 / 0。新导出文件显式保留这两个字段。导入的 Pitch Cents 会保存到普通 ENB 音符配置，后续 GUI / 指挥棒编辑其他参数时继续保留。

**恢复需要手动点击；尚未自动监听 Litematica 放置事件，也尚未实现 Workshop → Receiver 直接上传。** 仅支持包含 ENB 根元数据的原始 Paper Projection 文件。


## 无线红石

Wireless bus 按 World / Dimension 独立。相邻 Transmitter 与 Projection Receiver 可以形成 dedicated route：上升沿启动，下降沿停止，持续高电平不会在歌曲结束后自动循环；重新指向其他接收器时处理旧路由停止与新路由启动。

目前没有完整的 chunk-ticket 系统。
