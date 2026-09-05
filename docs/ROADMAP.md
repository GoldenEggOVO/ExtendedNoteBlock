# 待办与验证 · 2.8.0 基线

[返回首页](../README.md) · [2.8.0 更新说明](releases/2.8.0.md)

当前基线为 Full Fabric / Paper Client **2.8.0** 与 Paper Server **0.8.1**。本页区分已实现但待游戏验证的修复，以及尚未实现的功能。

## 已实现，待游戏内验证

- [ ] 同一 GM 乐器依次测试 MIDI **0 / 12 / 24 / 36 / 48 / 60 / 72 / 84 / 96 / 108 / 120**，确认低音八度不再被压成相同音高。
- [ ] 在实际 Purpur 26.2 中验证 Paper 音符盒界面的保存、128 键钢琴滚动与声音播放。
- [ ] 验证 Paper Projection Litematic 可加载和粘贴，ENB 载体位置不再因未知 ID 变成空气。

CI 成功只证明构建和已配置检查通过，不能代替以上游戏内验证。若低音仍有问题，继续检查 SoundEngine、`StoppablePositionalSoundInstance` 与 OpenAL pitch 路径。

## 下一步：Paper Litematic 自动导入

目标：Litematica Easy Place / paste 后，让已放置的原版载体恢复为有正确参数的 ENB 对象。

- [ ] Paper Client 读取 `ExtendedNoteBlockBridge` 元数据。
- [ ] 正确处理结构原点、维度、旋转 / 镜像等坐标变换。
- [ ] 实现分批上传、服务器权限与载体校验、导入结果反馈。
- [ ] 恢复对象、音符参数和投影数据，并验证重启后的持久化结果。

当前只有载体导出和元数据保留，以上导入能力尚未完成。

## 后续 Full Fabric → Paper 功能对齐

| 方向 | 当前已有 | 剩余工作 |
| --- | --- | --- |
| 音符盒 GUI | Full 风格基础编辑、GM 下拉、128 键钢琴 | 布局与翻译细节、高级曲线与表达式编辑 |
| 指挥棒 | Pos1 / Pos2、命令批量修改 | GUI、世界选区可视化、高级批量编辑 |
| Projection 上传 | Workshop 导出文件、服务器投影播放 | 选择 Receiver 后直接上传歌曲 |
| 无线红石 | 按世界隔离、真实红石源、dedicated route 边沿控制 | 完整区块加载维持方案 |

持续保持 Paper 的原版 Registry 安全，并在开发 Paper 功能时保留 Full Fabric 的真实方块与原有导出路径。
