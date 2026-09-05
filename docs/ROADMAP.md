# 待办与验证 · 2.9.0 基线

[返回首页](../README.md) · [2.9.0 更新说明](releases/2.9.0.md)

当前基线为 Full Fabric / Paper Client **2.9.0** 与 Paper Server **0.9.0**。

## 已收到的游戏反馈

用户已确认此前 Paper Client 的音高、GUI 和资源加载正常，Litematica 可以放下普通音符盒。此次更新处理粘贴后缺少 ENB 身份和参数的问题。

## Paper Litematic 恢复

- [x] 读取原始投影文件的 `ExtendedNoteBlockBridge` 元数据，兼容 2.8.x。
- [x] 以已粘贴的发射器定位，支持旋转 / 镜像；导入绑定当前世界。
- [x] 分批上传、权限 / 距离 / 载体 / 已加载区块校验和结果反馈。
- [x] 恢复对象、音符参数、Pitch Cents 和投影曲目，测试 YAML 重载持久化。
- [ ] 实际 Purpur + Litematica 中验证「N → 恢复 ENB」、模型更新、右键参数及拉杆播放。
- [ ] 实际服务器重启后确认对象、音符和投影仍可用。
- [ ] 可选 Litematica 放置事件集成，实现免手动点击恢复。

当前恢复面向 ENB 原始 Paper Projection 文件，普通 Litematic 不包含可还原的 ENB 音乐数据。

## 自动化验证

CI 启动实际 Paper Client JAR，等待资源加载，检查内置 Visuals、GUI、原版 Registry、全部 MIDI pitch 和原版声音行为，并用实际投影导出器生成文件后再读回验证。纯 Java 测试覆盖协议、参数边界、批次顺序、坐标变换。

模拟 Paper 测试调用真实插件注册的导入接收器，覆盖完整参数保存 / 重载、缺块、未加载区块、校验期间方块变化、权限、距离、切换世界和会话隔离。测试遇到模拟框架未实现 API 不允许以跳过方式通过。它们不能替代实际 Purpur 多人游戏验证。

## 后续 Full Fabric → Paper 功能对齐

| 方向 | 当前已有 | 剩余工作 |
| --- | --- | --- |
| 音符盒 GUI | Full 风格基础编辑、GM 下拉、128 键钢琴 | 高级曲线与表达式编辑 |
| 指挥棒 | Pos1 / Pos2、命令批量修改 | GUI、世界选区可视化、高级批量编辑 |
| Projection 上传 | Workshop 导出、粘贴后恢复、服务器投影播放 | 选择 Receiver 后直接上传歌曲，不放物理音符盒 |
| 无线红石 | 按世界隔离、真实红石源、dedicated route 边沿控制 | 完整区块加载维持方案 |

持续保持 Paper 的原版 Registry 安全，并保留 Full Fabric 的真实方块与原有导出路径。
