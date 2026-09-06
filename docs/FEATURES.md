# 功能展示

[文档中心](README.md) · [安装与使用](INSTALLATION.md) · [Paper 架构](ARCHITECTURE.md)

本页展示 Extended Note Block 的主要玩法与界面。截图主要来自 Full Fabric；Paper Client 已移植基础音符编辑与音乐工坊，但高级曲线和指挥棒 GUI 仍在对齐中。

## 扩展音符盒

每个 Extended Note Block 可设置 MIDI Note、GM Instrument、Velocity、Sustain、Delay、Fade In 和 Fade Out。Full Fabric 与 Paper Client 都覆盖 MIDI **0–127**，不受原版音符盒两八度范围限制。

![扩展音符盒编辑界面](assets/sh1.png)

Full Fabric 的高级设置支持音量曲线、Pitch Bend 曲线和表达式控制的声源移动。Paper 模式目前保留基础编辑与播放协议，高级曲线编辑仍列在路线图中。

![高级音量、弯音与声源位置设置](assets/sh2.png)

十二个音名拥有独立模型，通电时使用对应的发光变体。Paper Client 按服务器同步的坐标显示这些模型；无 Mod 玩家看到真实的原版载体。

![十二音名方块模型](assets/sh3.png)

![通电状态方块模型](assets/sh4.png)

## 指挥棒与批量编辑

指挥棒用于选择两个角点并批量修改区域内的 ENB 音符盒。Full Fabric 提供 GUI 与世界选区可视化；Paper Server 已支持 Pos1 / Pos2 和命令批量编辑。

![指挥棒批量编辑界面](assets/sh5.png)

大型结构可通过可视化射线确认两个选区点，并使用表达式批量生成参数变化。

![指挥棒 Pos1](assets/conductor/pos1.png)

![指挥棒 Pos2](assets/conductor/pos2.png)

![指挥棒表达式编辑](assets/conductor/exprx.png)

## 无线红石与投影

Global Redstone Transmitter / Receiver 可以跨距离传递红石状态，Projection Receiver 则用于控制 NBS 投影播放。Paper 模式按世界隔离无线总线，并使用真实红石方块提供强度 15 输出。

![无线红石与投影结构](assets/wireless.png)

## 音色包管理

Full Fabric 的音色包管理器可以切换、创建和编辑音色包，为不同 MIDI 音区指定 OGG 采样。

![音色包管理器](assets/soundpacks/soundpack_manager.png)

![编辑音色包](assets/soundpacks/edit_pack.png)

Paper 原版聆听模式使用服务器自动下发的组合资源包：128 个 GM program 映射到 32 种代表音色，每种提供 22 个半八度锚点，并包含 47 个独立打击乐采样。Paper Client 则继续使用完整 Mod 音色与高级声音控制。

## NBS 音乐工坊

默认按 **N** 打开音乐工坊，可导入 `.nbs`、`.mid`、`.midi`、WAV、MP3、OGG、AIFF / AIF 和 AU，进行试听、音域规划并导出 Litematic、结构 NBT 或数据包。

Paper Projection 使用原版载体保证 Registry 安全。粘贴完成后，可在音乐工坊选择 **恢复 ENB**，读取原始 `.litematic` 中的 `ExtendedNoteBlockBridge` 元数据并恢复全部参数。完整步骤见[Paper Litematic 恢复](INSTALLATION.md#paper-litematic-恢复-enb)。

## 不同模式的视觉边界

| 场景 | 物品外观 | 已放置方块 | 声音 |
| --- | --- | --- | --- |
| Full Fabric | 完整 ENB 模型 | 真实 ENB 方块模型 | 完整 Mod 音色与高级控制 |
| Paper Client | 完整 ENB 模型 | 按坐标替换为完整 ENB 模型 | 完整 Bridge 音色与高级控制 |
| Paper 原版客户端 | 服务器资源包提供 ENB 物品材质 | 音符盒、混凝土等真实载体 | 32 种代表音色覆盖 MIDI 0–127 |

服务器资源包不会覆盖普通 Note Block 的世界方块状态，也不会创建 Display Entity。这样可以避免假方块刷新和实体数量带来的性能、交互与重载问题。
