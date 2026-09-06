# 安装与使用 · Minecraft 26.2

[返回首页](../README.md) · [Paper 架构](ARCHITECTURE.md) · [待办与验证](ROADMAP.md)

## 下载与依赖

从 [v2.11.0-mc26.2 Release](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.11.0-mc26.2) 下载所需文件。Release 同时提供 `SHA256SUMS.txt`。

| 文件 | 安装对象 |
| --- | --- |
| `ExtendedNoteBlock-Full-Fabric-2.11.0-mc26.2.jar` | 单人 / Fabric 服务端模式 |
| `ExtendedNoteBlock-Paper-Client-Fabric-2.11.0-mc26.2.jar` | Paper / Purpur 专用 Fabric 客户端 |
| `ExtendedNoteBlock-Paper-Server-0.11.0-mc26.2.jar` | Paper / Purpur 服务端，内置自动资源包配置 |
| `ExtendedNoteBlock-Server-Resources-2.11.0-mc26.2.zip` | 插件自动下发的组合材质与聆听音色包 |
| `ExtendedNoteBlock-Visuals-2.11.0-mc26.2.zip` | 不含声音的可选独立物品资源包 |

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
3. 普通客户端进服时接受 ENB 服务器资源包；无需安装 Mod 即可听音乐并看到轻量 ENB 方块材质。
4. 需要编辑、精确世界方块模型与完整表现力时，再安装 Fabric Loader、Fabric API 和 Paper Client JAR；Full Fabric 与 Paper Client 二选一。
5. OP 在游戏内运行 `/enb give all`，放置 ENB 物品后右键测试编辑界面。

官方 Paper Server JAR 已写入同版本 `Server-Resources` 的 HTTPS 地址与 SHA-1。默认 `resource-pack.enabled: true`、`required: true`、`use-official-release: true`；即使保留了旧版 `config.yml`，插件也会采用当前 JAR 内嵌的正式资源包地址。若客户端对该服务器设置为“启用”，资源包会静默下载而不弹确认框；设为“提示”才会显示确认框。插件会在聊天与控制台显示请求及最终状态，可用 `/enb pack status` 检查、`/enb pack resend` 重发。

自定义托管时，将 `resource-pack.use-official-release` 改为 `false`，并同时填写自定义 UUID、HTTPS URL 与 ZIP 的 40 位 SHA-1。`/enb reload` 会重新读取配置并向在线玩家下发。

组合包包含与 Visuals 同源的方块 / 物品模型和材质，以及原版客户端聆听音色。成功加载后，Paper Server 会把已登记 ENB 的坐标以客户端假 Note Block 状态批量发送：关闭时六面均为 `a_top.png`，通电时六面均为满亮显示的 `a_top_on.png`。真实世界方块仍是音符盒，不产生展示实体，红石灯与绝大多数原版状态保持原样；模型满亮不等于向周围投射真实光照。唯一的资源包级冲突是极少见的普通 `custom_head + note 24` 音符盒状态也会使用该模型，但不会改变其世界数据或红石行为。

Paper Client 用户不会使用这套简化的假状态和组合包，而是继续走 Mod 声音协议及按音高区分的原始世界模型。资源包玩家若要看到新方块外观，需要同时更新对应版本的 Paper Server 和自动下发的 `Server-Resources`；单独安装轻量 Visuals ZIP 仍主要用于物品外观。

原版聆听模式将全部 128 个 GM 乐器编号映射到 32 种代表音色，每种使用 11 个跨八度锚点覆盖 MIDI 0–127，另含 47 个打击乐音色。它保留乐器类别、音高、力度、延音、延迟和基础空间位置；连续音高 / 音量曲线与移动声源仍以 Paper Client 最完整。

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
| `/enb pack status` | 查看资源包启用状态、来源、校验值及自己的加载状态 |
| `/enb pack resend` | 向自己重新发送资源包请求；控制台执行时向所有在线玩家发送 |
| `/enb pack test <MIDI 0-127> [instrument 0-127]` | 不经过音符盒，直接试听资源包中的指定音高和音色 |

可批量编辑属性：`note`、`instrument`、`velocity`、`sustain`、`delay`、`fadein`、`fadeout`。默认最大选区体积为 **262144**，由服务器配置控制；具体参数可用 TAB 补全和 `/enb help wand` 查看。

## 音乐工坊目录

下列路径相对于所用游戏实例的游戏目录；启动器隔离实例时，不一定是默认 `.minecraft`。

| 路径 | 内容 |
| --- | --- |
| `extendednoteblock/songs/` | 歌曲文件 |
| `schematics/extendednoteblock/` | 结构输出 |
| `extendednoteblock/datapacks/` | 数据包输出 |

默认 `SIX_OCTAVES` 是 Projection Planner 的音域适配策略，范围 **24–95**；`TWO_OCTAVES` 为 **54–78**。它们不改变 ENB 音符盒本身 **MIDI 0–127** 的输入范围。

## Paper Litematic 恢复 ENB

需要 **Paper Client 2.9.0+** 与 **Paper Server 0.9.0+**。此功能读取 Paper 音乐工坊导出的原始 ENB Projection `.litematic`；兼容 2.8.0 / 2.8.1 保留的元数据。

1. 用 Litematica 的 paste / Easy Place 完整放好投影结构，包含底部的红色混凝土发射器和相邻的紫色混凝土接收器。
2. 看向该红色混凝土（或它上面的拉杆），按 **N → 恢复 ENB**。界面会预填所指方块的坐标；核对它是结构底部发射器的坐标，也可手动修改 X / Y / Z。
3. 点击「浏览」，选择**原始 ENB 工坊导出的文件**。默认列出游戏目录 `schematics/` 下的 `.litematic`；其他位置可直接输入完整文件路径后点「读取」。
4. 没有改变方向时保持旋转 0°、镜像无。旋转 / 镜像过的结构，选择对应变换：先沿源结构 X / Z 轴镜像，再绕 Y 轴顺时针旋转。定位点始终是已粘贴的红色发射器，与 Litematica 原点设置无关。
5. 点「恢复 ENB」，等待服务器显示已保存。ENB 音符盒、发射器、投影接收器会恢复逻辑身份和客户端模型；右键音符盒可查看恢复的 MIDI / 乐器 / 力度 / 延音 / 延迟等参数，拉杆可触发投影曲目。

恢复权限为 `extendednoteblockbridge.import`，默认 OP。服务器默认要求玩家距发射器不超过 64 格，最多 75000 个音符，同时只处理一个导入；配置项为 `litematic-import`。只处理当前世界的已加载区块，不强制加载或生成区块；大型结构需保持整个目标区域已加载，必要时由服主调整服务器模拟距离或加载区域后重试。

服务器先核对每个目标位置及载体，再登记 ENB 数据，分别保存至 `objects.yml`、`notes.yml`、`projections.yml`。缺块、方向错误、目标未加载会显示坐标并停止，修正后重试即可。重复恢复会用文件中的参数覆盖该投影对应的 ENB 数据。文件仍使用原版载体，Paper Client 继续保持 Registry 安全。

普通原版 Litematic、Full Fabric 自定义方块导出，以及被其他工具重新保存后丢失 `ExtendedNoteBlockBridge` 根元数据的文件，不能从普通音符盒推算出原来的音乐参数。此时请重新从 Paper 工坊导出，并选择与已粘贴结构一致的文件。新功能需要手动点击恢复，尚未自动监听 Litematica 每次 Easy Place，也尚未实现不放置物理音符盒的 Workshop → Receiver 直接上传。

## 常见问题

### 连接 Paper 时因未知 ENB Registry ID 断线

检查客户端是否误装 Full Fabric，或同时装了两个 ENB 客户端版本。Paper Client 使用原版注册表，不向 Paper 注册自定义方块 / 物品。

### 粘贴结构后是普通音符盒，没有原来的音乐参数

Litematica 只放置原版载体。2.9.0 新增「恢复 ENB」入口，可将原始投影文件中的参数登记回 Paper Server 0.9.0。请按上方恢复步骤操作；仅更新客户端而未更新插件时，恢复按钮不可用。

### MIDI 低音区听起来仍然相同

Paper Client 请确认版本为 2.11.0 或更新；原版客户端先执行 `/enb pack status`，必须看到 `SUCCESSFULLY_LOADED (MIDI 0-127 listener enabled)`。可用同一乐器依次试听 **0 / 12 / 24 / 36 / 48 / 60 / 72 / 84 / 96 / 108 / 120**。若拒绝或未成功加载组合包，插件只会播放原版音符盒回退，低音区仍受原版限制。MIDI 0–15 的物理频率低于或接近人耳与普通扬声器下限，即使技术映射正确，也可能几乎听不到。

### 启动出现 IllegalClassLoadError

Paper Client 2.8.0 的 Mixin 配置错误地覆盖了普通入口类所在的包。移除旧 JAR，改用 2.8.1 或更新版本；这不需要更换存档或 Java 版本。不要同时保留两个 Paper Client JAR，也不要同时安装 Full Fabric。

### 编辑界面提示没有权限

Paper Server 0.8.2 开始，GUI 保存与 `/enb` 命令统一检查 `extendednoteblockbridge.use`，默认仅 OP。服主可以通过权限插件将此权限授予需要编辑的玩家。原有 `objects.yml`、`notes.yml`、`projections.yml` 格式保持兼容。
