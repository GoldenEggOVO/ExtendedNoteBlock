# CraftEngine 与 Litematica 完整复制实施计划

> 按 superpowers:subagent-driven-development 执行；用户已确认方案并授权发布。

**目标：** Paper Client 保存建筑时附带 ENB 参数，粘贴建筑及音乐工坊投影时自动恢复；Paper Server 必需 CraftEngine。
**架构：** Litematica 26.2 可选兼容 mixin 接入保存/粘贴生命周期。共享有界协议传输服务端权威 ENB 快照，保存在 litematic 扩展元数据，按子区域坐标变换导入。CE 使用现有集成和自有资源，正式构建可复现。
**技术：** Minecraft 26.2 / Java 25 / Fabric Loader 0.19.5 / CraftEngine 26.8.2 / Litematica 0.28.8。
**规格：** 本对话已确认的两种复制流程，保留完整音符配置及接收器时间轴；Full Fabric 保持独立。

- [x] CE 发布集成：独立 prepare_paper_craftengine.py、bridge 依赖及 ENB 资源，验证生成幂等、旧配置保留及 bridge 构建。
- [x] 快照协议与服务端：有界编解码、分包、权限和坐标/区块校验、导出完整数据、导入原子预检及持久化。回归覆盖截断/超限、参数往返、独立方块及投影。
- [x] 客户端 Litematica：保存前异步获取快照，NBT 扩展字段读写，成功粘贴后自动导入；支持区域启用、原点、镜像、旋转；失败明确提示。用真实 0.28.8 JAR 编译验证可选 mixin。
- [x] 打包文档：2.13.0 / Paper Server 0.14.0，更新依赖说明、兼容边界及发布工作流；不分发第三方私有插件。
- [x] 验证发布：Python 回归、Gradle test/build、打包内容和客户端启动检查、独立代码审查；推送精确源码、执行发布、核对 Release 资产。

验证结果：服务端 49 项、客户端 10 项、Python 29 项测试全部通过；GitHub Actions 完成含/不含 Litematica 的真实客户端启动检查。五个发布附件 SHA-256 与清单一致，服务端内置资源包 SHA-1 与发布包一致。

已发布：https://github.com/GoldenEggOVO/ExtendedNoteBlock/releases/tag/v2.13.0-mc26.2

实机多人交互与试听尚未验收；本次未部署或重启运行中的服务器。
