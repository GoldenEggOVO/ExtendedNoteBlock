# 安装与使用 · Minecraft 26.2

[返回首页](../README.md) · [文档中心](README.md) · [功能展示](FEATURES.md) · [Paper 架构](ARCHITECTURE.md)

## 下载与依赖

从 [v2.13.2-mc26.2 Release](https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.13.2-mc26.2) 下载所需文件。Release 同时提供 `SHA256SUMS.txt`。

| 文件 | 安装对象 |
| --- | --- |
| `ExtendedNoteBlock-Full-Fabric-2.13.2-mc26.2.jar` | 单人 / Fabric 服务端模式 |
| `ExtendedNoteBlock-Paper-Client-Fabric-2.13.2-mc26.2.jar` | Paper / Purpur 专用 Fabric 客户端 |
| `ExtendedNoteBlock-Paper-Server-0.14.2-mc26.2.jar` | Paper / Purpur 服务端，内置强制自动资源包配置 |
| `ExtendedNoteBlock-CraftEngine-2.13.2-mc26.2.zip` | 安装到服务端的 ENB 注册配置、模型与全部聆听音色，交由 CraftEngine 生成完整包 |

| 环境 | 本版本构建基线 |
| --- | --- |
| Minecraft | 26.2 |
| Java | 25 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.159.0+26.2 |
| 插件服务端 | Paper / Purpur 26.2 |
| Paper Server 必需插件 | CraftEngine 26.8.2 |
| 自动复制客户端依赖 | Litematica 0.28.8 + MaLiLib 0.29.6 |

**CraftEngine 是 Paper Server 必需依赖，缺少它时插件不会加载。Full Fabric 不需要 CraftEngine。** ENB 发布包不包含 CraftEngine JAR，请从作者渠道安装；Release 的 `ExtendedNoteBlock-CraftEngine-2.13.2-mc26.2.zip` 只提供 ENB 注册配置和自有资源。

Fabric API 只用于 Fabric 客户端 / 服务端，不放入 Paper 的 `plugins/`。上述版本是当前构建基线，并非对其他版本兼容性的承诺。

## Full Fabric

1. 安装目标版本的 Fabric Loader、Fabric API 和 Java。
2. 将 Full Fabric JAR 放入所用游戏实例的 `mods/`。
3. Fabric 多人服务器也安装同版 Full Fabric 与 Fabric API。

Full Fabric 注册真正的 ENB 方块和物品。连接纯 Paper / Purpur 服务器时应使用 Paper Client。

## Paper / Purpur

1. 正常停服，备份 `plugins/ExtendedNoteBlockBridge/` 和 `plugins/CraftEngine/`，移出旧版 ENB JAR。
2. 将 Paper Server 和 CraftEngine 26.8.2 JAR 放入 `plugins/`，解压 ENB CraftEngine ZIP 到服务器根目录，确认资源落在 `plugins/CraftEngine/resources/enb/`。启动服务器，按下文配置生成、托管及唯一发送方，再重建资源包。
3. 普通客户端进服时加载 ENB 服务器资源包；无需安装 Mod 即可听音乐并看到 ENB 物品材质。
4. 需要编辑、精确世界方块模型与完整表现力时，再安装 Fabric Loader、Fabric API 和 Paper Client JAR；Full Fabric 与 Paper Client 二选一。
5. OP 在游戏内运行 `/enb give all`，放置 ENB 物品后右键测试编辑界面。

### Paper 服务器资源包

从 Paper Server **0.14.1** 起，玩家只下载 **CraftEngine 生成并托管的完整包**，其中包括 ENB 方块映射、模型和全部聆听音色。ENB 通过 CraftEngine API 获取生成文件和玩家下载地址，校验两者 SHA-1 一致后下发，并跟踪加载成功、失败和拒绝状态；不再使用 GitHub `Server-Resources` 地址。

在 `plugins/CraftEngine/config.yml` 的现有 `resource-pack.delivery` 节点中设置以下两项，保留原有托管配置：

```yaml
resource-pack:
  delivery:
    send-on-join: false
    resend-on-upload: false
```

CraftEngine 负责生成和托管，ENB 负责下发这一份包。若上述自动发送仍开启，ENB 会提示配置冲突并暂停自己的发送，避免重复加载。更新配置后正常重启服务器，生成并上传完整包；下载地址必须让玩家能够访问。无需在 ENB 中填写 URL、SHA-1 或生成路径。

ENB 默认 `resource-pack.enabled: true`、`required: true`。旧配置中的 `url`、`sha1`、`id`、`use-official-release`、`combined-file` 不再生效，无需删除已有音乐数据。新包生成或托管尚未完成时等待匹配的包，不会退回旧的独立包。保留正常 ZIP 格式和 ENB 资源路径；破坏 CRC 或重命名 ENB 资源路径的保护设置会使完整性校验失败。

可用 `/enb pack status` 查看状态、`/enb pack resend` 重发。资源包实际成功加载后才启用原版 MIDI 聆听，Paper Client 用户继续走 Mod 声音协议。GitHub Releases 只保留当前正式版附件；旧版独立包下载地址已停用，使用旧插件的服务器应升级。2.13.1 起不再发布独立 `Server-Resources` 附件。

原版聆听模式将全部 128 个 GM 乐器编号映射到 32 种代表音色，每种使用 22 个半八度锚点覆盖 MIDI 0–127，另含 47 个打击乐音色。常规播放最多只需约 ±3 半音的实时变调；采样使用 OGG quality 4、高精度离线重采样和短尾部淡出。位置音频所需的单声道取自已居中的合成器主声道，避免部分立体声效果在左右相加时发生相位抵消。它保留乐器类别、音高、力度、延音、延迟和基础空间位置；连续音高 / 音量曲线与移动声源仍以 Paper Client 最完整。

更新插件时保留插件数据目录。`objects.yml`、`notes.yml` 和 `projections.yml` 分别涉及对象登记、音符参数及投影数据，新客户端通过专用元数据自动保存/导入这些登记；不带 ENB 元数据的普通复制无法推算原来的参数。旧登记方块按预算迁移到 CraftEngine；迁移前保留数据备份和原方块日志。

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

自动复制需要 **Paper Client 2.13.2 + Paper Server 0.14.2 + CraftEngine 26.8.2**，客户端另装 **Litematica 0.28.8 + MaLiLib 0.29.6**。Litematica 对不使用复制功能的玩家是可选依赖。

### 复制已有建筑

1. 在 Litematica 中选择含 ENB 方块的区域，正常保存 `.litematic`。
2. 客户端先读取服务器的 ENB 参数快照，再将数据连同各子区域写入文件。等待保存完成；权限或网络错误会明确提示。
3. 加载文件、设置目标原点与旋转/镜像，用 Litematica 的命令粘贴功能完整粘贴。将 `pasteReplaceBehavior` 设为 **All**，关闭 **changed-block-only**。如果服务器装有 LitematicaFolia / Servux，还需将 `pasteUsingServux` 设为 **false**，以使用本版本接入的命令粘贴通道。带 ENB 参数时不支持其他替换模式，客户端会在发送命令前提示并取消。
4. 粘贴完成后 ENB 自动提交参数。等待 ENB 成功消息，再右键音符盒检查参数或触发接收器试听。

### 粘贴音乐工坊投影

在 Paper Client 音乐工坊导出 `.litematic`，用 Litematica 加载并完整粘贴；客户端读取文件中的 `ExtendedNoteBlockBridge` 元数据，自动恢复音符和控制器。原始 2.8.0+ Paper 工坊文件可沿用。

复制保留 MIDI、乐器、力度、延音、延迟、淡入淡出、Pitch Cents、无线红石方块身份和投影接收器时间轴。坐标随原点、子区域位置、旋转和镜像变换。无线红石继续遵循 ENB 当前世界的全局联动规则；复制不会创建新的独立频道。

### 限制与手动恢复

- 复制/导入权限 `extendednoteblockbridge.import` 默认仅 OP；目标区块必须已加载，服务端校验范围、数据大小和方块类型。
- 保存时仅将 ENB 坐标转换为稳定的原版载体并附带参数，粘贴后由服务端还原为 CraftEngine 方块；不修改选区中的普通方块。
- 自动导入接入命令粘贴的完成事件；Servux Direct Paste、Easy Place 逐块搭建、WorldEdit 独立命令或其他插件的独立粘贴不会触发该事件。
- Easy Place 搭建工坊投影后，可按 **N → 恢复 ENB**，选择原始投影文件，填入发射器坐标及旋转/镜像，手动恢复。此入口保留兼容用途。
- 未包含 ENB 参数的旧建筑投影、Full Fabric 自定义方块投影，不能从载体外观推算音乐参数。使用新客户端重新保存原建筑。
- 第三方工具重新保存文件时可能删除 ENB 扩展字段。保留原始 `.litematic`，不要把外观相同当作参数完整。
- 缺块、未加载或 CraftEngine 注册资源未就绪会使导入失败并提示；修正原因后重新完整粘贴。

## 语言设置

Full Fabric 与 Paper Client 的界面由 Minecraft 客户端语言设置决定，目前内置英语和简体中文。Paper Server 默认使用英语，第一次启动会生成 `plugins/ExtendedNoteBlockBridge/lang/en_us.yml` 与 `zh_cn.yml`。服主可编辑消息 YAML，并在 `plugins/ExtendedNoteBlockBridge/config.yml` 中设置 `language: en_us` 或 `language: zh_cn`。执行 `/enb reload` 后生效。CraftEngine 的 ENB 物品名称可在 `plugins/CraftEngine/resources/enb/configuration/enb.yml` 修改；修改后重新生成并上传 CraftEngine 资源包。

## 常见问题

### 连接 Paper 时因未知 ENB Registry ID 断线

检查客户端是否误装 Full Fabric，或同时装了两个 ENB 客户端版本。Paper Client 使用原版注册表，不向 Paper 注册自定义方块 / 物品。

### 粘贴结构后是普通音符盒，没有原来的音乐参数

确认客户端、插件、CraftEngine 和 Litematica 版本匹配，ENB 注册资源已加载，并等待 ENB 自动导入成功消息。旧文件若没有 ENB 元数据，需要重新保存源建筑；Easy Place 使用上方手动恢复步骤。

### MIDI 低音区听起来仍然相同

Paper Client 请确认版本为 2.13.2 或更新；原版客户端先执行 `/enb pack status`，必须看到 `SUCCESSFULLY_LOADED (MIDI 0-127 listener enabled)`。可用同一乐器依次试听 **0 / 6 / 12 / 24 / 36 / 48 / 60 / 72 / 84 / 96 / 108 / 120 / 126 / 127**。若资源包未成功加载，插件只会播放原版音符盒回退，低音区仍受原版限制；默认 `required: true` 时拒绝资源包会被服务器断开连接。MIDI 0–15 的物理频率低于或接近人耳与普通扬声器下限，即使技术映射正确，也可能几乎听不到。

### 启动出现 IllegalClassLoadError

Paper Client 2.8.0 的 Mixin 配置错误地覆盖了普通入口类所在的包。移除旧 JAR，改用 2.8.1 或更新版本；这不需要更换存档或 Java 版本。不要同时保留两个 Paper Client JAR，也不要同时安装 Full Fabric。

### 编辑界面提示没有权限

Paper Server 0.8.2 开始，GUI 保存与 `/enb` 命令统一检查 `extendednoteblockbridge.use`，默认仅 OP。服主可以通过权限插件将此权限授予需要编辑的玩家。原有 `objects.yml`、`notes.yml`、`projections.yml` 格式保持兼容。

## 相关文档

- [功能展示](FEATURES.md)
- [Paper / Purpur 架构](ARCHITECTURE.md)
- [路线图与验证](ROADMAP.md)
- [2.13.2 发布说明](releases/2.13.2.md)
