# Paper / Purpur 架构

[返回首页](../README.md) · [文档中心](README.md) · [功能展示](FEATURES.md) · [开发指南](DEVELOPMENT_zh-cn.md) · [当前待办](ROADMAP.md)

## 三个程序版本

| 版本 | Registry 与运行职责 |
| --- | --- |
| Full Fabric | 注册真实 `extendednoteblock:*` Block、Item、BlockEntity 与 Menu，包含完整 Fabric 内容 |
| Paper Client | Fabric 客户端伴侣，复用声音、资源与客户端工具；打包时排除 Full Registry 依赖 |
| Paper Server | Paper / Purpur 插件，以原版方块和物品为载体，负责逻辑身份、数据、播放与同步 |

Full Fabric 与 Paper Client 不应同时安装。Paper 的编辑界面可以复制 Full 的视觉和交互，但不能直接继承依赖 Full Menu Registry 的容器界面；保存使用独立 Bridge Payload。

## CraftEngine 方块与 ENB 数据

Paper Server 0.14.0 必须安装 CraftEngine 26.8.2。CraftEngine 注册 `enb:extended_note_block`、`enb:global_redstone_transmitter`、`enb:global_redstone_receiver`、`enb:nbs_projection_receiver` 与指挥棒物品，管理世界方块身份和资源包模型。ENB 的 `objects.yml`、`notes.yml`、`projections.yml` 继续保存完整音乐行为；注册方块状态本身不能代表这些参数。

旧版原版载体根据已有 ENB 登记按每tick预算迁移，先备份数据并记录原始方块状态。普通世界方块不会因材质相同而被扫描转换。复制导入只能处理同型 ENB 或允许的原版载体，不能覆写其他 CraftEngine 自定义方块身份。

## 物品外观与世界方块外观

服主将 Release 中 ENB CraftEngine 注册资源安装后，由 CraftEngine 根据该服务器的映射生成和下发最终资源包。原版玩家因此也可看到 ENB 世界方块。ENB 官方音色包使用可叠加的 `addResourcePack` 请求，避免替换服务器已下发的 CraftEngine 包。可选合并包需要服主提供真实生成文件和公开URL。

Paper Client 保持原版 Registry，同时保留 ENB 模型和音频协议。登录、切换世界和频道注册时同步对象快照；对象变化时增量更新。声音按有无 Paper Client 分流，避免重复播放。

## 声音

Paper Server 保存 MIDI Note、Instrument、Velocity、Sustain、Delay、Fade In、Fade Out 与导入的 Pitch Cents。高级 Bridge 声音协议还提供 pitch multiplier、pitch cents、音量 / 空间位置更新和 start / update / stop。

Paper Client 从最近的采样音符计算 `2^(半音差 / 12)`。2.8.0 添加专用、Registry-safe 的 SoundEngine Mixin，对 `extendednoteblock` 声音绕过 Minecraft 的最终 pitch clamp；2.8.1 修复 Mixin 包冲突，并将 48 格衰减限定到 ENB 声音。

2.12.0 的组合资源包为 128 个 GM program 每四个映射一种代表音色，共 32 种；每种预渲染 MIDI 0、6、…、126 共 22 个锚点，将常规实时变调压缩到约 ±3 半音，同时覆盖 MIDI 0–127。另有 MIDI 35–81 共 47 个独立打击乐采样。每个事件提供 8 个逻辑别名，让服务端能独立停止常见的同音重叠而不复制 OGG。物理采样采用 OGG quality 4、高精度 SoXR 离线重采样、尾部淡出和有上限的峰值归一化；构建会逐个解码检查并拒绝达到 50,000,000 bytes 的包。

Paper Server 在玩家加入 40 ticks 后发送带固定 UUID、HTTPS URL 与 SHA-1 的资源包请求，并只在收到 `SUCCESSFULLY_LOADED` 后向该玩家发送 `extendednoteblock_listener:*` 声音。正式 URL / SHA-1 来自 JAR 内独立 metadata，默认不受旧磁盘配置覆盖；`/enb pack status|resend` 提供诊断和重发。加载中、拒绝或失败时保留 Note Block fallback；检测到 Paper Client 插件频道的玩家继续走 Bridge 声音协议，避免重复播放。原版模式不实时还原高级音量曲线、连续弯音或表达式声源移动。

## Litematic 导出与导入边界

| 输出模式 | Palette 内容 | 当前行为 |
| --- | --- | --- |
| Full Fabric | 真实 `extendednoteblock:*` 方块 | 使用 Full 方块与对应数据 |
| Paper Client | Note Block、红色混凝土、紫色混凝土 | 避免未知 Registry ID 被解析为空气 |

Paper Projection `.litematic` 根 NBT 额外保存 `ExtendedNoteBlockBridge`：相对坐标、MIDI、GM 乐器、力度、延音、延迟、pitch cents 与 Projection Timeline。

Paper Client 2.13.0 的可选 Litematica 0.28.8 兼容层在保存前获取服务器权威快照，按子区域保存 ENB 扩展元数据。在命令粘贴成功结束后，将区域启用状态、原点、子区域位置、旋转、镜像和层范围应用到坐标，再上传完整数据。工坊投影原有 `ExtendedNoteBlockBridge` 根字段继续兼容；普通建筑使用独立的 `ExtendedNoteBlockSchematic` 根字段。

新协议通过 `extendednoteblock:schematic` 请求快照或上传分片，通过 `extendednoteblock:schematic_result` 返回数据及结果。UUID绑定会话，长度、条目和时间轴有上限；跨世界、超时、错误顺序和截断均拒绝。服务端校验导入权限、范围、区块、方块类型和位置唯一性，全部目标预检通过后才修改。CraftEngine 转换中途失败会回滚原始物理方块，再开始任何 ENB 登记修改。

数据写回现有 YAML，成功消息以保存结果为准。每个文件使用临时文件替换；这不是跨三个文件的数据库事务。磁盘保存失败会报告失败，服主应修复磁盘问题后重试。

旧的 `bridge_import` / `bridge_import_status` 手动恢复协议保留，用于 Easy Place 搭建的原始 Paper 工坊投影。自动流程不监听逐块 Easy Place，不接管其他插件的独立粘贴，也不能恢复已经丢失的 ENB 元数据。

## 无线红石

Wireless bus 按 World / Dimension 独立。相邻 Transmitter 与 Projection Receiver 可以形成 dedicated route：上升沿启动，下降沿停止，持续高电平不会在歌曲结束后自动循环；重新指向其他接收器时处理旧路由停止与新路由启动。

目前没有完整的 chunk-ticket 系统。
